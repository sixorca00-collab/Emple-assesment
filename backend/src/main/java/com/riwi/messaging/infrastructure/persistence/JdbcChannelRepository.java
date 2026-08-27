package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import com.riwi.messaging.domain.model.ChannelView;
import com.riwi.messaging.domain.model.ConversationPage;
import com.riwi.messaging.domain.model.ConversationView;
import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.NewChannel;
import com.riwi.messaging.domain.port.ChannelRepository;
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

// adaptador JdbcTemplate del puerto de canales; todo el SQL va parametrizado y sin OFFSET
@Repository
public class JdbcChannelRepository implements ChannelRepository {

    // orden de conversaciones: mas reciente primero, con channel_id como desempate estable
    private static final String CONVERSATION_COLUMNS = """
            channel_id, channel_name, is_private, my_role,
            last_message_id, last_message_preview, last_message_sender_id, last_message_at,
            unread_count,
            COALESCE(last_message_at, 'epoch'::timestamptz) AS sort_ts
            """;

    private static final String CONVERSATIONS_FIRST_PAGE = """
            SELECT %s FROM rw_user_conversation
            ORDER BY COALESCE(last_message_at, 'epoch'::timestamptz) DESC, channel_id DESC
            LIMIT :limit
            """.formatted(CONVERSATION_COLUMNS);

    private static final String CONVERSATIONS_NEXT_PAGE = """
            SELECT %s FROM rw_user_conversation
            WHERE (COALESCE(last_message_at, 'epoch'::timestamptz), channel_id) < (:cursorTs, :cursorId)
            ORDER BY COALESCE(last_message_at, 'epoch'::timestamptz) DESC, channel_id DESC
            LIMIT :limit
            """.formatted(CONVERSATION_COLUMNS);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcChannelRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConversationPage listConversations(Cursor after, int limit) {
        var params = new MapSqlParameterSource();
        // pedimos una fila extra para saber si hay pagina siguiente sin usar OFFSET
        params.addValue("limit", limit + 1);

        String sql = CONVERSATIONS_FIRST_PAGE;
        if (after != null) {
            sql = CONVERSATIONS_NEXT_PAGE;
            params.addValue("cursorTs", OffsetDateTime.ofInstant(after.timestamp(), ZoneOffset.UTC));
            params.addValue("cursorId", after.id());
        }

        // la vista rw_user_conversation ya limita a las conversaciones del actor (security_invoker + RLS)
        List<ConversationView> rows = jdbc.query(sql, params, conversationMapper());
        return toPage(rows, limit);
    }

    @Override
    public ChannelView createWithOwner(NewChannel channel) {
        // generamos el id en la app: asi evitamos un RETURNING que la RLS de SELECT bloquearia
        // (el creador aun no es miembro y el canal puede ser privado)
        UUID channelId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
                .addValue("id", channelId)
                .addValue("name", channel.name())
                .addValue("description", channel.description())
                .addValue("isPrivate", channel.isPrivate());

        // created_by = actor de la transaccion; la politica RLS de INSERT lo exige
        DbFunctionErrors.mapping(() -> jdbc.update("""
                INSERT INTO rw_channel (id, name, description, is_private, created_by)
                VALUES (:id, :name, :description, :isPrivate, rw_current_user_id())
                """, params));

        // dejamos al creador como owner del canal en la misma transaccion
        jdbc.update("""
                INSERT INTO rw_channel_member (channel_id, user_id, role)
                VALUES (:id, rw_current_user_id(), 'owner')
                """, params);

        // ahora el actor ya es miembro: la RLS de SELECT permite leer el canal recien creado
        return jdbc.queryForObject("""
                SELECT id, name, description, is_private, created_at
                FROM rw_channel
                WHERE id = :id
                """, params, channelMapper("owner"));
    }

    @Override
    public void addMember(UUID channelId, UUID userId, String role) {
        var params = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                .addValue("userId", userId)
                .addValue("role", role);

        // solo owner/admin del canal puede agregar miembros; el chequeo vive en la BD
        Boolean isAdmin = jdbc.queryForObject(
                "SELECT rw_is_channel_admin(:channelId)", params, Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new NotAuthorizedException("only an owner or admin can add members to this channel");
        }

        // el usuario objetivo debe existir y estar vigente
        Boolean userExists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM rw_user WHERE id = :userId AND deleted_at IS NULL)",
                params, Boolean.class);
        if (!Boolean.TRUE.equals(userExists)) {
            throw new ResourceNotFoundException("user not found");
        }

        // alta idempotente: si ya es miembro no hacemos nada
        jdbc.update("""
                INSERT INTO rw_channel_member (channel_id, user_id, role)
                VALUES (:channelId, :userId, :role)
                ON CONFLICT (channel_id, user_id) DO NOTHING
                """, params);
    }

    private ConversationPage toPage(List<ConversationView> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<ConversationView> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        Cursor next = hasMore
                ? new Cursor(items.get(items.size() - 1).sortKey(), items.get(items.size() - 1).channelId())
                : null;
        return new ConversationPage(items, next);
    }

    private RowMapper<ConversationView> conversationMapper() {
        return (ResultSet rs, int rowNum) -> new ConversationView(
                rs.getObject("channel_id", UUID.class),
                rs.getString("channel_name"),
                rs.getBoolean("is_private"),
                rs.getString("my_role"),
                rs.getObject("last_message_id", UUID.class),
                rs.getString("last_message_preview"),
                rs.getObject("last_message_sender_id", UUID.class),
                instantOrNull(rs.getObject("last_message_at", OffsetDateTime.class)),
                rs.getLong("unread_count"),
                rs.getObject("sort_ts", OffsetDateTime.class).toInstant());
    }

    private RowMapper<ChannelView> channelMapper(String myRole) {
        return (ResultSet rs, int rowNum) -> new ChannelView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("is_private"),
                myRole,
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static java.time.Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
