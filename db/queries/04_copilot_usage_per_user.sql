-- ============================================================
-- Consulta 4: consumo acumulado del copiloto por usuario
-- ============================================================
-- Que resuelve:
--   Reporte de uso del copiloto por usuario: numero de consultas, cuantas fueron
--   respondidas o rechazadas y total de tokens consumidos (prompt + completion).
--
-- Parametros (rango opcional; ambos NULL = historico completo):
--   :from  timestamptz  -> inicio del periodo (inclusive), o NULL
--   :to    timestamptz  -> fin del periodo (exclusivo), o NULL
--
-- Como cumple las restricciones:
--   - total_tokens es columna GENERATED (prompt_tokens + completion_tokens): la suma es siempre consistente.
--   - Todo parametrizado; sin OFFSET (es una agregacion, se pagina por keyset sobre user_id si hace falta).
--   - Pensada para un rol de reporte / administrador; rw_copilot_query no expone contenido de canales.
--
-- Nota de implementacion (backend):
--   GET /copilot/usage ejecuta esta agregacion anadiendo en el WHERE el filtro de alcance:
--   un actor no administrador solo ve su propia fila (q.user_id = :actor_id); is_platform_admin
--   puede pasar ?userId= para desglosar por usuario o ver todas. El rango :from/:to sigue siendo opcional.

SELECT
    q.user_id,
    p.display_name,
    p.job_title,
    count(*)                                                                   AS query_count,
    count(*) FILTER (WHERE q.status = 'answered')                              AS answered_count,
    count(*) FILTER (WHERE q.status IN ('refused_no_context', 'refused_permission')) AS refused_count,
    count(*) FILTER (WHERE q.status = 'error')                                 AS error_count,
    coalesce(sum(q.prompt_tokens), 0)                                          AS prompt_tokens,
    coalesce(sum(q.completion_tokens), 0)                                      AS completion_tokens,
    coalesce(sum(q.total_tokens), 0)                                           AS total_tokens,
    max(q.created_at)                                                          AS last_query_at
FROM rw_copilot_query q
JOIN rw_user_profile p ON p.user_id = q.user_id
WHERE (CAST(:from AS timestamptz) IS NULL OR q.created_at >= CAST(:from AS timestamptz))
  AND (CAST(:to   AS timestamptz) IS NULL OR q.created_at <  CAST(:to   AS timestamptz))
GROUP BY q.user_id, p.display_name, p.job_title
ORDER BY total_tokens DESC, q.user_id;
