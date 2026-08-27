-- ============================================================
-- Consulta 2: busqueda de mensajes con resaltado del termino encontrado
-- ============================================================
-- Que resuelve:
--   Busca mensajes que coinciden con el texto buscado y devuelve un fragmento
--   con el termino resaltado (<mark>...</mark>) para pintarlo directo en el frontend.
--
-- Parametros:
--   :q          text     -> texto de busqueda del usuario (se trata como dato no confiable)
--   :after_rank real     -> cursor: rank del ultimo resultado ya mostrado (NULL en la 1a pagina)
--   :after_id   uuid     -> cursor: id del ultimo resultado ya mostrado (NULL en la 1a pagina)
--   :page_size  integer  -> tamano de pagina
--
-- Como cumple las restricciones:
--   - websearch_to_tsquery convierte el texto libre en tsquery de forma segura (sin construir SQL).
--   - ts_headline genera el resaltado del termino.
--   - Keyset sobre (rank DESC, id DESC); NUNCA usa OFFSET.
--   - deleted_at IS NULL => no aparecen mensajes con soft delete.
--   - Permisos: con el actor fijado por  SET LOCAL app.current_user_id, la RLS de rw_message
--     filtra primero por membresia; la busqueda no puede filtrar contenido de canales ajenos.

WITH q AS (
    SELECT websearch_to_tsquery('spanish', :q) AS tsq
)
SELECT
    m.id,
    m.channel_id,
    c.name AS channel_name,
    m.sender_id,
    ts_headline(
        'spanish',
        m.body,
        q.tsq,
        'StartSel=<mark>, StopSel=</mark>, MaxFragments=2, MinWords=3, MaxWords=18, FragmentDelimiter= ... '
    ) AS snippet,
    ts_rank(m.search_tsv, q.tsq) AS rank,
    m.created_at
FROM rw_message m
JOIN rw_channel c ON c.id = m.channel_id
CROSS JOIN q
WHERE m.deleted_at IS NULL
  AND m.search_tsv @@ q.tsq
  AND (
        CAST(:after_rank AS real) IS NULL
        OR (ts_rank(m.search_tsv, q.tsq), m.id) < (CAST(:after_rank AS real), CAST(:after_id AS uuid))
      )
ORDER BY rank DESC, m.id DESC
LIMIT :page_size;
