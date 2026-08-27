-- V3__functions.sql
-- Logica de negocio dentro de la BD: funciones transaccionales que validan permisos,
-- la vista de conversaciones del usuario y los procedimientos almacenados de gestion de usuarios.
-- Toda funcion corre con permisos del invocador => las politicas RLS de V2 siguen aplicando.

SET TIME ZONE 'UTC';


-- ============================================================
-- Vista: conversaciones del usuario autenticado
-- security_invoker => la RLS de rw_channel / rw_message se evalua como el actor que consulta
-- ============================================================
CREATE OR REPLACE VIEW rw_user_conversation
WITH (security_invoker = true) AS
SELECT
    c.id            AS channel_id,
    c.name          AS channel_name,
    c.is_private    AS is_private,
    cm.role         AS my_role,
    lm.id           AS last_message_id,
    lm.body         AS last_message_preview,
    lm.sender_id    AS last_message_sender_id,
    lm.created_at   AS last_message_at,
    -- mensajes ajenos vivos del canal que el actor aun no ha marcado como leidos
    (
        SELECT count(*)
        FROM rw_message m
        WHERE m.channel_id = c.id
          AND m.deleted_at IS NULL
          AND m.sender_id <> cm.user_id
          AND NOT EXISTS (
              SELECT 1
              FROM rw_message_read_receipt r
              WHERE r.message_id = m.id
                AND r.user_id = cm.user_id
          )
    )               AS unread_count
FROM rw_channel c
-- el join por membresia del actor es lo que limita la vista a "sus" conversaciones
JOIN rw_channel_member cm
    ON cm.channel_id = c.id
   AND cm.user_id = rw_current_user_id()
-- ultimo mensaje vivo del canal, sin OFFSET
LEFT JOIN LATERAL (
    SELECT m.id, m.body, m.sender_id, m.created_at
    FROM rw_message m
    WHERE m.channel_id = c.id
      AND m.deleted_at IS NULL
    ORDER BY m.created_at DESC, m.id DESC
    LIMIT 1
) lm ON true
WHERE c.deleted_at IS NULL;

GRANT SELECT ON rw_user_conversation TO riwi_app;


-- ============================================================
-- Funcion transaccional: publicar mensaje
-- valida la membresia EN LA BD; idempotente por client_nonce
-- ============================================================
CREATE OR REPLACE FUNCTION rw_post_message(
    p_channel_id   uuid,
    p_body         text,
    p_client_nonce uuid DEFAULT NULL
)
RETURNS rw_message
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor  uuid := rw_current_user_id();
    v_result rw_message;
BEGIN
    -- exige actor autenticado fijado por la transaccion
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    -- validacion de permiso en la BD (ademas de la politica RLS de INSERT)
    IF NOT rw_is_channel_member(p_channel_id) THEN
        RAISE EXCEPTION 'actor % is not a member of channel %', v_actor, p_channel_id USING errcode = '42501';
    END IF;

    -- reenvio del mismo mensaje: devolvemos el existente sin duplicar
    IF p_client_nonce IS NOT NULL THEN
        SELECT * INTO v_result
        FROM rw_message
        WHERE sender_id = v_actor
          AND client_nonce = p_client_nonce;
        IF FOUND THEN
            RETURN v_result;
        END IF;
    END IF;

    -- insertamos el mensaje ya como 'sent' (el estado 'pending'/'failed' lo maneja el emisor)
    INSERT INTO rw_message (channel_id, sender_id, body, status, client_nonce)
    VALUES (p_channel_id, v_actor, p_body, 'sent', p_client_nonce)
    RETURNING * INTO v_result;

    RETURN v_result;
END;
$$;


-- ============================================================
-- Funcion transaccional: editar mensaje propio
-- ============================================================
CREATE OR REPLACE FUNCTION rw_edit_message(
    p_message_id uuid,
    p_new_body   text
)
RETURNS rw_message
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor  uuid := rw_current_user_id();
    v_result rw_message;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    -- la RLS de SELECT ya limita lo visible; aqui solo distinguimos casos de error
    SELECT * INTO v_result FROM rw_message WHERE id = p_message_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'message % not found or not visible', p_message_id USING errcode = 'P0002';
    END IF;

    IF v_result.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'cannot edit a deleted message' USING errcode = '55000';
    END IF;

    -- solo el autor edita el contenido
    IF v_result.sender_id <> v_actor THEN
        RAISE EXCEPTION 'only the author can edit message %', p_message_id USING errcode = '42501';
    END IF;

    -- el trigger de V4 resincroniza search_tsv e invalida el embedding
    UPDATE rw_message
    SET body = p_new_body,
        edited_at = now()
    WHERE id = p_message_id
    RETURNING * INTO v_result;

    RETURN v_result;
END;
$$;


-- ============================================================
-- Funcion transaccional: soft delete de mensaje (NUNCA borrado fisico)
-- ============================================================
CREATE OR REPLACE FUNCTION rw_soft_delete_message(p_message_id uuid)
RETURNS rw_message
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor  uuid := rw_current_user_id();
    v_result rw_message;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    SELECT * INTO v_result FROM rw_message WHERE id = p_message_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'message % not found or not visible', p_message_id USING errcode = 'P0002';
    END IF;

    IF v_result.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'message % is already deleted', p_message_id USING errcode = '55000';
    END IF;

    -- el autor o un admin del canal (moderacion)
    IF v_result.sender_id <> v_actor AND NOT rw_is_channel_admin(v_result.channel_id) THEN
        RAISE EXCEPTION 'not allowed to delete message %', p_message_id USING errcode = '42501';
    END IF;

    -- marcamos borrado conservando el body original para auditoria
    UPDATE rw_message
    SET deleted_at = now(),
        deleted_by = v_actor
    WHERE id = p_message_id
    RETURNING * INTO v_result;

    RETURN v_result;
END;
$$;


-- ============================================================
-- Funcion transaccional: marcar como leidos los mensajes de un canal
-- ============================================================
CREATE OR REPLACE FUNCTION rw_mark_channel_read(p_channel_id uuid)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor uuid := rw_current_user_id();
    v_count integer;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    IF NOT rw_is_channel_member(p_channel_id) THEN
        RAISE EXCEPTION 'actor % is not a member of channel %', v_actor, p_channel_id USING errcode = '42501';
    END IF;

    -- insertamos acuse por cada mensaje ajeno vivo que aun no tenga acuse del actor
    INSERT INTO rw_message_read_receipt (message_id, user_id, read_at)
    SELECT m.id, v_actor, now()
    FROM rw_message m
    WHERE m.channel_id = p_channel_id
      AND m.deleted_at IS NULL
      AND m.sender_id <> v_actor
    ON CONFLICT (message_id, user_id) DO NOTHING;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$;

GRANT EXECUTE ON FUNCTION rw_post_message(uuid, text, uuid)   TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_edit_message(uuid, text)         TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_soft_delete_message(uuid)        TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_mark_channel_read(uuid)          TO riwi_app;


-- ============================================================
-- Stored procedure (a): consulta de usuarios con keyset pagination
-- orden estable por (display_name, user_id); NUNCA usa OFFSET
-- ============================================================
CREATE OR REPLACE FUNCTION rw_search_users(
    p_query                text    DEFAULT NULL,
    p_after_display_name   text    DEFAULT NULL,
    p_after_id             uuid    DEFAULT NULL,
    p_limit                integer DEFAULT 20,
    p_include_inactive     boolean DEFAULT false
)
RETURNS TABLE (
    user_id      uuid,
    display_name text,
    job_title    text,
    avatar_url   text,
    is_active    boolean,
    created_at   timestamptz
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_actor        uuid    := rw_current_user_id();
    v_is_admin     boolean := rw_is_platform_admin();
    -- patron parametrizado (no es SQL dinamico ni concatenacion de SQL)
    v_pattern      text    := '%' || coalesce(btrim(p_query), '') || '%';
    v_limit        integer := least(greatest(coalesce(p_limit, 20), 1), 100);
    v_want_inactive boolean := p_include_inactive AND v_is_admin;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    RETURN QUERY
    SELECT u.id, p.display_name, p.job_title, p.avatar_url, u.is_active, u.created_at
    FROM rw_user u
    JOIN rw_user_profile p ON p.user_id = u.id
    WHERE u.deleted_at IS NULL
      AND (v_want_inactive OR u.is_active = true)
      AND (
            p_query IS NULL
            OR p.display_name ILIKE v_pattern
            OR p.job_title    ILIKE v_pattern
            -- el correo solo se usa como filtro para administradores de plataforma
            OR (v_is_admin AND u.email ILIKE v_pattern)
          )
      -- condicion de keyset: fila estrictamente posterior al cursor recibido
      AND (
            p_after_id IS NULL
            OR (lower(p.display_name), u.id) > (lower(coalesce(p_after_display_name, '')), p_after_id)
          )
    ORDER BY lower(p.display_name), u.id
    LIMIT v_limit;
END;
$$;

GRANT EXECUTE ON FUNCTION rw_search_users(text, text, uuid, integer, boolean) TO riwi_app;


-- ============================================================
-- Stored procedure (b.1): edicion de usuario
-- transaccional: cualquier fallo revierte y conserva los estados originales
-- ============================================================
CREATE OR REPLACE PROCEDURE rw_update_user(
    p_target       uuid,
    p_display_name text    DEFAULT NULL,
    p_job_title    text    DEFAULT NULL,
    p_avatar_url   text    DEFAULT NULL,
    p_bio          text    DEFAULT NULL,
    p_is_active    boolean DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor    uuid    := rw_current_user_id();
    v_is_admin boolean := rw_is_platform_admin();
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    -- solo el propio usuario o un administrador de plataforma
    IF v_actor <> p_target AND NOT v_is_admin THEN
        RAISE EXCEPTION 'not allowed to update user %', p_target USING errcode = '42501';
    END IF;

    -- cambiar el estado activo/inactivo es exclusivo de administradores
    IF p_is_active IS NOT NULL AND NOT v_is_admin THEN
        RAISE EXCEPTION 'only a platform admin can change is_active' USING errcode = '42501';
    END IF;

    -- subtransaccion: si algo falla dentro, se revierte a este punto y se relanza el error
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM rw_user WHERE id = p_target AND deleted_at IS NULL) THEN
            RAISE EXCEPTION 'user % not found or already deleted', p_target USING errcode = 'P0002';
        END IF;

        -- COALESCE => los parametros NULL dejan el valor actual intacto
        UPDATE rw_user_profile
        SET display_name = coalesce(p_display_name, display_name),
            job_title    = coalesce(p_job_title, job_title),
            avatar_url   = coalesce(p_avatar_url, avatar_url),
            bio          = coalesce(p_bio, bio)
        WHERE user_id = p_target;

        IF p_is_active IS NOT NULL THEN
            UPDATE rw_user
            SET is_active = p_is_active
            WHERE id = p_target;
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            -- no dejamos rastros parciales: se revierte y se propaga
            RAISE;
    END;
END;
$$;


-- ============================================================
-- Stored procedure (b.2): eliminacion de usuario (SOFT DELETE)
-- conserva mensajes, membresias, acuses de lectura e historial del copiloto
-- ============================================================
CREATE OR REPLACE PROCEDURE rw_delete_user(p_target uuid)
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor    uuid    := rw_current_user_id();
    v_is_admin boolean := rw_is_platform_admin();
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'no authenticated actor' USING errcode = '28000';
    END IF;

    IF v_actor <> p_target AND NOT v_is_admin THEN
        RAISE EXCEPTION 'not allowed to delete user %', p_target USING errcode = '42501';
    END IF;

    BEGIN
        IF NOT EXISTS (SELECT 1 FROM rw_user WHERE id = p_target AND deleted_at IS NULL) THEN
            RAISE EXCEPTION 'user % not found or already deleted', p_target USING errcode = 'P0002';
        END IF;

        -- soft delete: marcamos y desactivamos, nunca DELETE fisico
        UPDATE rw_user
        SET deleted_at = now(),
            is_active  = false
        WHERE id = p_target;

        -- cerramos sesiones: revocamos los refresh tokens vigentes del usuario
        UPDATE rw_refresh_token
        SET revoked_at = now()
        WHERE user_id = p_target
          AND revoked_at IS NULL;

        -- se conservan a proposito rw_message, rw_channel_member, rw_message_read_receipt y rw_copilot_query
    EXCEPTION
        WHEN OTHERS THEN
            RAISE;
    END;
END;
$$;

GRANT EXECUTE ON PROCEDURE rw_update_user(uuid, text, text, text, text, boolean) TO riwi_app;
GRANT EXECUTE ON PROCEDURE rw_delete_user(uuid)                                   TO riwi_app;
