-- ============================================================
-- Consulta 3: recuperacion de contexto para el copiloto con permisos aplicados EN SQL
-- ============================================================
-- Que resuelve:
--   Dado el embedding de la pregunta del usuario, devuelve los mensajes mas similares
--   que el copiloto puede citar como fuente, restringidos a lo que el actor puede ver.
--
-- Parametros:
--   :query_embedding  text/vector   -> embedding de la pregunta como literal '[v1,v2,...]' (lo calcula EmbeddingPort)
--   :actor_id         uuid          -> id del actor, tomado EXCLUSIVAMENTE del JWT
--   :match_count      integer       -> cantidad maxima de fragmentos de contexto
--   :min_similarity   real          -> umbral minimo de similitud coseno (para negativa honesta si no hay contexto)
--
-- Como cumple las restricciones:
--   - Filtro de permisos EN SQL: el EXISTS contra rw_channel_member exige que :actor_id sea
--     miembro del canal del mensaje. No se delega la seguridad solo al codigo Java.
--   - Defensa en profundidad: ademas, el backend fija  SET LOCAL app.current_user_id = :actor_id,
--     por lo que la politica RLS p_rw_message_select vuelve a filtrar por membresia.
--   - deleted_at IS NULL => nunca se cita un mensaje borrado.
--   - Orden por distancia coseno con el indice HNSW ix_rw_message_embedding; LIMIT, sin OFFSET.
--
-- Nota de implementacion (backend):
--   El adaptador JdbcCopilotContextRepository ejecuta esta misma consulta parametrizada.
--   Si no devuelve filas, el copiloto consulta rw_copilot_context_exists_elsewhere (V5, SECURITY
--   DEFINER) para distinguir "no hay contexto" (refused_no_context) de "existe pero no para ti"
--   (refused_permission). Esa funcion ignora la membresia y solo devuelve un booleano.

SELECT
    m.id            AS message_id,
    m.channel_id,
    c.name          AS channel_name,
    m.sender_id,
    p.display_name  AS author_name,
    p.job_title     AS author_job_title,
    m.body,
    m.created_at,
    1 - (m.embedding <=> CAST(:query_embedding AS vector)) AS similarity
FROM rw_message m
JOIN rw_channel c       ON c.id = m.channel_id
JOIN rw_user_profile p  ON p.user_id = m.sender_id
WHERE m.deleted_at IS NULL
  AND m.embedding IS NOT NULL
  -- permiso explicito en SQL: el actor debe ser miembro del canal del mensaje
  AND EXISTS (
        SELECT 1
        FROM rw_channel_member cm
        WHERE cm.channel_id = m.channel_id
          AND cm.user_id = :actor_id
      )
  AND (1 - (m.embedding <=> CAST(:query_embedding AS vector))) >= CAST(:min_similarity AS real)
ORDER BY m.embedding <=> CAST(:query_embedding AS vector)
LIMIT :match_count;
