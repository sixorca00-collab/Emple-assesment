package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.MessagePage;
import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// adaptador JdbcTemplate del puerto de mensajes; se apoya en las funciones transaccionales de la BD
@Repository
public class JdbcMessageRepository implements MessageRepository {

    // proyeccion comun con el nombre del emisor resuelto
    private static final String MESSAGE_SELECT = """
            SELECT m.id, m.channel_id, m.sender_id, p.display_name AS sender_name,
                   m.body, m.status, m.created_at, m.edited_at
            """;

    // Consulta 1: historial descendente por (created_at, id); usa el indice parcial de mensajes vivos
    private static final String HISTORY_FIRST_PAGE = MESSAGE_SELECT + """
            FROM rw_message m
            JOIN rw_user_profile p ON p.user_id = m.sender_id
            WHERE m.channel_id = :channelId
              AND m.deleted_at IS NULL
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """;

    private static final String HISTORY_NEXT_PAGE = MESSAGE_SELECT + """
            FROM rw_message m
            JOIN rw_user_profile p ON p.user_id = m.sender_id
            WHERE m.channel_id = :channelId
              AND m.deleted_at IS NULL
              AND (m.created_at, m.id) < (:beforeCreatedAt, :beforeId)
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMessageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MessagePage history(UUID channelId, Cursor before, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                // una fila extra para detectar la pagina siguiente sin OFFSET
                .addValue("limit", limit + 1);

        // permiso explicito en SQL: un no-miembro recibe 403 en vez de una pagina vacia
        Boolean member = jdbc.queryForObject(
                "SELECT rw_is_channel_member(:channelId)", params, Boolean.class);
        if (!Boolean.TRUE.equals(member)) {
            throw new NotAuthorizedException("not a member of channel " + channelId);
        }

        String sql = HISTORY_FIRST_PAGE;
        if (before != null) {
            sql = HISTORY_NEXT_PAGE;
            params.addValue("beforeCreatedAt", OffsetDateTime.ofInstant(before.timestamp(), ZoneOffset.UTC));
            params.addValue("beforeId", before.id());
        }

        // la RLS p_rw_message_select limita las filas a canales donde el actor es miembro
        List<MessageView> rows = jdbc.query(sql, params, messageMapper());

        boolean hasMore = rows.size() > limit;
        List<MessageView> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        Cursor next = hasMore
                ? new Cursor(items.get(items.size() - 1).createdAt(), items.get(items.size() - 1).id())
                : null;
        return new MessagePage(items, next);
    }

    @Override
    public MessageView post(UUID channelId, String body, UUID clientNonce) {
        var params = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                .addValue("body", body)
                .addValue("nonce", clientNonce);

        // rw_post_message valida membresia en la BD y deduplica por (sender_id, client_nonce)
        String sql = MESSAGE_SELECT + """
                FROM rw_post_message(:channelId, :body, CAST(:nonce AS uuid)) AS m
                JOIN rw_user_profile p ON p.user_id = m.sender_id
                """;
        return DbFunctionErrors.mapping(() -> jdbc.queryForObject(sql, params, messageMapper()));
    }

    @Override
    public MessageView edit(UUID messageId, String newBody) {
        var params = new MapSqlParameterSource()
                .addValue("messageId", messageId)
                .addValue("body", newBody);

        // rw_edit_message solo deja editar al autor
        String sql = MESSAGE_SELECT + """
                FROM rw_edit_message(:messageId, :body) AS m
                JOIN rw_user_profile p ON p.user_id = m.sender_id
                """;
        return DbFunctionErrors.mapping(() -> jdbc.queryForObject(sql, params, messageMapper()));
    }

    @Override
    public void softDelete(UUID messageId) {
        var params = new MapSqlParameterSource().addValue("messageId", messageId);
        // rw_soft_delete_message marca deleted_at/deleted_by; nunca borra fisicamente
        DbFunctionErrors.mapping(() ->
                jdbc.queryForObject("SELECT rw_soft_delete_message(:messageId)", params, (rs, n) -> rs.getObject(1)));
    }

    @Override
    public int markChannelRead(UUID channelId) {
        var params = new MapSqlParameterSource().addValue("channelId", channelId);
        // rw_mark_channel_read valida membresia y devuelve cuantos acuses inserto
        Integer marked = DbFunctionErrors.mapping(() ->
                jdbc.queryForObject("SELECT rw_mark_channel_read(:channelId)", params, Integer.class));
        return marked == null ? 0 : marked;
    }

    private RowMapper<MessageView> messageMapper() {
        return (ResultSet rs, int rowNum) -> new MessageView(
                rs.getObject("id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("sender_id", UUID.class),
                rs.getString("sender_name"),
                rs.getString("body"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                editedAt(rs.getObject("edited_at", OffsetDateTime.class)));
    }

    private static java.time.Instant editedAt(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
