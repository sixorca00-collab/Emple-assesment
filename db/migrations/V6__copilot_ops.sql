-- V6__copilot_ops.sql
-- Operacion del copiloto RAG:
--  1) re-embedding TOTAL de mensajes vivos (modo "all" del backfill: sobrescribe los vectores sinteticos del seed)
--  2) cobertura de embeddings para el endpoint de readiness del copiloto

SET TIME ZONE 'UTC';


-- ============================================================
-- Backfill modo "all": lista TODOS los mensajes vivos para re-embedding
-- ============================================================
-- SECURITY DEFINER: igual que rw_messages_missing_embedding, el backend debe ver mensajes de
-- todos los canales; la RLS de SELECT lo impediria. No filtra por embedding: reprocesa todo.
CREATE OR REPLACE FUNCTION rw_messages_for_reembedding(p_limit integer DEFAULT 500)
RETURNS TABLE (message_id uuid, body text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT m.id, m.body
    FROM rw_message m
    WHERE m.deleted_at IS NULL
    ORDER BY m.created_at
    LIMIT least(greatest(coalesce(p_limit, 500), 1), 5000)
$$;


-- ============================================================
-- Readiness del copiloto: cobertura de embeddings sobre el corpus vivo
-- ============================================================
-- SECURITY DEFINER: cuenta global (ignora RLS) para el panel de verificacion previo a la demo.
CREATE OR REPLACE FUNCTION rw_message_embedding_stats()
RETURNS TABLE (total_messages bigint, messages_with_embedding bigint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        count(*) FILTER (WHERE m.deleted_at IS NULL),
        count(*) FILTER (WHERE m.deleted_at IS NULL AND m.embedding IS NOT NULL)
    FROM rw_message m
$$;


-- ============================================================
-- Privilegios: solo el rol de aplicacion ejecuta estas funciones
-- ============================================================
REVOKE ALL ON FUNCTION rw_messages_for_reembedding(integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION rw_message_embedding_stats()         FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw_messages_for_reembedding(integer) TO riwi_app;
GRANT EXECUTE ON FUNCTION rw_message_embedding_stats()         TO riwi_app;
