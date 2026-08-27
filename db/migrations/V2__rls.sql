-- V2__rls.sql
-- Rol de aplicacion SIN superusuario y SIN BYPASSRLS + Row Level Security sobre canales y mensajes.
-- El actor se fija por transaccion con: SET LOCAL app.current_user_id = '<uuid>'
-- (o set_config('app.current_user_id', '<uuid>', true) desde una funcion).

SET TIME ZONE 'UTC';


-- ============================================================
-- Rol de aplicacion
-- ============================================================
-- Las migraciones se corren con un superusuario bootstrap (POSTGRES_USER != riwi_app).
-- El backend se conecta SIEMPRE como riwi_app, que aqui se crea/asegura sin privilegios peligrosos.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'riwi_app') THEN
        -- password por defecto solo para arranque local; el despliegue debe rotarla
        CREATE ROLE riwi_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT
            PASSWORD 'riwi_app';
        RAISE NOTICE 'rol riwi_app creado con password por defecto; rotarla en el despliegue';
    END IF;
END
$$;

-- el rol de aplicacion nunca puede saltarse RLS ni escalar privilegios
DO $$
BEGIN
    ALTER ROLE riwi_app WITH NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT;
EXCEPTION
    WHEN OTHERS THEN
        -- ocurre si riwi_app ES el superusuario bootstrap: configuracion no recomendada
        RAISE WARNING 'no se pudo retirar SUPERUSER/BYPASSRLS de riwi_app: usa un superusuario bootstrap distinto para migrar';
END
$$;

-- permisos minimos: leer/insertar/actualizar, NUNCA DELETE (borrado fisico prohibido)
GRANT USAGE ON SCHEMA public TO riwi_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO riwi_app;
-- lo mismo para tablas futuras creadas por el owner de la migracion
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO riwi_app;


-- ============================================================
-- Helpers de identidad usados por las politicas RLS
-- ============================================================

-- actor autenticado de la transaccion actual; NULL si no se ha fijado
CREATE OR REPLACE FUNCTION rw_current_user_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
    -- leemos el GUC de sesion/transaccion; el segundo argumento evita error si no existe
    SELECT NULLIF(current_setting('app.current_user_id', true), '')::uuid
$$;

-- fija el actor de la transaccion; la usan el backend y el cargador de seed
CREATE OR REPLACE FUNCTION rw_set_current_user(p_user_id uuid)
RETURNS void
LANGUAGE sql
AS $$
    -- true => alcance local a la transaccion (equivale a SET LOCAL)
    SELECT set_config('app.current_user_id', p_user_id::text, true)
$$;

-- membresia del actor en un canal; SECURITY DEFINER para consultar la tabla puente sin depender de RLS
CREATE OR REPLACE FUNCTION rw_is_channel_member(p_channel_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM rw_channel_member cm
        WHERE cm.channel_id = p_channel_id
          AND cm.user_id = rw_current_user_id()
    )
$$;

-- el actor es owner/admin del canal (para editar canal o moderar mensajes ajenos)
CREATE OR REPLACE FUNCTION rw_is_channel_admin(p_channel_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM rw_channel_member cm
        WHERE cm.channel_id = p_channel_id
          AND cm.user_id = rw_current_user_id()
          AND cm.role IN ('owner', 'admin')
    )
$$;

-- el actor es administrador de plataforma (solo para procedimientos de gestion de usuarios)
CREATE OR REPLACE FUNCTION rw_is_platform_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM rw_user u
        WHERE u.id = rw_current_user_id()
          AND u.is_platform_admin = true
          AND u.deleted_at IS NULL
    )
$$;

REVOKE ALL ON FUNCTION rw_is_channel_member(uuid)  FROM PUBLIC;
REVOKE ALL ON FUNCTION rw_is_channel_admin(uuid)   FROM PUBLIC;
REVOKE ALL ON FUNCTION rw_is_platform_admin()      FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw_current_user_id()          TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_set_current_user(uuid)     TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_is_channel_member(uuid)    TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_is_channel_admin(uuid)     TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_is_platform_admin()        TO riwi_app;


-- ============================================================
-- RLS sobre rw_channel
-- ============================================================
ALTER TABLE rw_channel ENABLE ROW LEVEL SECURITY;
-- FORCE: el owner de la tabla (que corre las migraciones) tambien queda sujeto a las politicas
ALTER TABLE rw_channel FORCE ROW LEVEL SECURITY;

-- se ve un canal si esta vigente y es publico, o si el actor es miembro
CREATE POLICY p_rw_channel_select ON rw_channel
    FOR SELECT
    USING (
        deleted_at IS NULL
        AND (is_private = false OR rw_is_channel_member(id))
    );

-- solo puedes crear un canal a tu nombre
CREATE POLICY p_rw_channel_insert ON rw_channel
    FOR INSERT
    WITH CHECK (created_by = rw_current_user_id());

-- editar/soft-delete de canal: solo owner/admin del canal
CREATE POLICY p_rw_channel_update ON rw_channel
    FOR UPDATE
    USING (rw_is_channel_admin(id))
    WITH CHECK (rw_is_channel_admin(id));

-- no se define politica de DELETE: nadie puede borrar canales por esta via


-- ============================================================
-- RLS sobre rw_message
-- ============================================================
ALTER TABLE rw_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw_message FORCE ROW LEVEL SECURITY;

-- se leen los mensajes SOLO de canales donde el actor es miembro (incluye el copiloto/RAG)
CREATE POLICY p_rw_message_select ON rw_message
    FOR SELECT
    USING (rw_is_channel_member(channel_id));

-- solo puedes publicar a tu nombre y en un canal donde eres miembro
CREATE POLICY p_rw_message_insert ON rw_message
    FOR INSERT
    WITH CHECK (
        sender_id = rw_current_user_id()
        AND rw_is_channel_member(channel_id)
    );

-- editar o soft-delete: el autor del mensaje, o un admin del canal (moderacion)
CREATE POLICY p_rw_message_update ON rw_message
    FOR UPDATE
    USING (sender_id = rw_current_user_id() OR rw_is_channel_admin(channel_id))
    WITH CHECK (sender_id = rw_current_user_id() OR rw_is_channel_admin(channel_id));

-- no se define politica de DELETE: el borrado fisico de mensajes esta prohibido


-- ============================================================
-- Refuerzo del "no borrado fisico" a nivel de privilegios
-- ============================================================
-- por si el backend llegara a conectarse con un rol que no sea el owner de la tabla
-- (no se revoca TRUNCATE: lo usa el cargador de seed para recargas limpias en entornos no productivos)
REVOKE DELETE ON rw_message FROM PUBLIC;
REVOKE DELETE ON rw_message FROM riwi_app;
