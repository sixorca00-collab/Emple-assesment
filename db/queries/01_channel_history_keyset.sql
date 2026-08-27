-- ============================================================
-- Consulta 1: historial de mensajes de un canal con keyset pagination
-- ============================================================
-- Que resuelve:
--   Devuelve una pagina del historial de un canal, del mas nuevo al mas viejo,
--   lista para "cargar mas hacia arriba" preservando el scroll en el frontend.
--
-- Parametros (nombrados, siempre parametrizados; nunca concatenacion de SQL):
--   :channel_id         uuid         -> canal a leer
--   :before_created_at  timestamptz  -> cursor: created_at del ultimo mensaje ya cargado (NULL en la 1a pagina)
--   :before_id          uuid         -> cursor: id del ultimo mensaje ya cargado (NULL en la 1a pagina)
--   :page_size          integer      -> tamano de pagina
--
-- Como cumple las restricciones:
--   - Keyset puro sobre (created_at DESC, id DESC): comparacion de tuplas < (cursor). NUNCA usa OFFSET.
--   - Usa el indice parcial ix_rw_message_channel_keyset (solo filas vivas).
--   - deleted_at IS NULL => nunca muestra mensajes con soft delete.
--   - Permisos: el actor se fija antes con  SET LOCAL app.current_user_id = '<uuid del JWT>'.
--     La politica RLS p_rw_message_select ya limita las filas a canales donde el actor es miembro,
--     asi que un no-miembro recibe 0 filas aunque conozca el :channel_id.

SELECT
    m.id,
    m.channel_id,
    m.sender_id,
    p.display_name AS sender_name,
    m.body,
    m.status,
    m.created_at,
    m.edited_at
FROM rw_message m
JOIN rw_user_profile p ON p.user_id = m.sender_id
WHERE m.channel_id = :channel_id
  AND m.deleted_at IS NULL
  -- primera pagina: ambos cursores NULL -> se ignora la condicion
  AND (
        CAST(:before_created_at AS timestamptz) IS NULL
        OR (m.created_at, m.id) < (CAST(:before_created_at AS timestamptz), CAST(:before_id AS uuid))
      )
ORDER BY m.created_at DESC, m.id DESC
LIMIT :page_size;
