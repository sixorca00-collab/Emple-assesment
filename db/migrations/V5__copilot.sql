-- V5__copilot.sql
-- Soporte de datos del copiloto RAG:
--  1) version del system prompt con la que se genero cada respuesta (auditoria de prompt engineering)
--  2) backfill de embeddings sin exponer la RLS de rw_message
--  3) senal de "existe contexto, pero fuera del alcance del actor" para la negativa por permisos

SET TIME ZONE 'UTC';


-- ============================================================
-- Version del system prompt usada por consulta del copiloto
-- ============================================================
-- default 'v1': las filas ya cargadas por el seed quedan marcadas con la primera version
ALTER TABLE rw_copilot_query
    ADD COLUMN system_prompt_version text NOT NULL DEFAULT 'v1';

ALTER TABLE rw_copilot_query
    ADD CONSTRAINT ck_rw_copilot_query_prompt_version
    CHECK (char_length(btrim(system_prompt_version)) BETWEEN 1 AND 20);


-- ============================================================
-- Backfill de embeddings: lista de mensajes vivos sin vector
-- ============================================================
-- SECURITY DEFINER (owner = superusuario de migracion): el backend necesita ver TODOS los
-- mensajes pendientes, no solo los de sus canales, y la RLS de SELECT lo impediria.
CREATE OR REPLACE FUNCTION rw_messages_missing_embedding(p_limit integer DEFAULT 200)
RETURNS TABLE (message_id uuid, body text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT m.id, m.body
    FROM rw_message m
    WHERE m.deleted_at IS NULL
      AND m.embedding IS NULL
    ORDER BY m.created_at
    LIMIT least(greatest(coalesce(p_limit, 200), 1), 1000)
$$;


-- ============================================================
-- Backfill de embeddings: fija el vector calculado por el backend
-- ============================================================
-- SECURITY DEFINER: la RLS de UPDATE de rw_message es por autoria del mensaje; el backfill
-- actualiza mensajes de cualquier autor, asi que corre con los privilegios del owner.
CREATE OR REPLACE FUNCTION rw_set_message_embedding(p_message_id uuid, p_embedding vector)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- solo mensajes vivos; no reescribe el body, asi que el trigger de search_tsv no se dispara
    UPDATE rw_message
    SET embedding = p_embedding
    WHERE id = p_message_id
      AND deleted_at IS NULL;
END;
$$;


-- ============================================================
-- Negativa honesta: distingue "no hay nada" de "hay algo, pero no para ti"
-- ============================================================
-- SECURITY DEFINER: ignora deliberadamente la membresia para saber si el contexto EXISTE
-- en algun canal. Devuelve solo un booleano, nunca contenido de mensajes.
CREATE OR REPLACE FUNCTION rw_copilot_context_exists_elsewhere(
    p_query_embedding vector,
    p_min_similarity  real
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM rw_message m
        WHERE m.deleted_at IS NULL
          AND m.embedding IS NOT NULL
          AND (1 - (m.embedding <=> p_query_embedding)) >= p_min_similarity
    )
$$;


-- ============================================================
-- Privilegios: solo el rol de aplicacion ejecuta estas funciones
-- ============================================================
REVOKE ALL ON FUNCTION rw_messages_missing_embedding(integer)              FROM PUBLIC;
REVOKE ALL ON FUNCTION rw_set_message_embedding(uuid, vector)              FROM PUBLIC;
REVOKE ALL ON FUNCTION rw_copilot_context_exists_elsewhere(vector, real)   FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw_messages_missing_embedding(integer)            TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_set_message_embedding(uuid, vector)            TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_copilot_context_exists_elsewhere(vector, real) TO riwi_app;
