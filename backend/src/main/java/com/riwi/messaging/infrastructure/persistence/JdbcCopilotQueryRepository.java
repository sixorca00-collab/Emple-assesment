package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.CopilotCitation;
import com.riwi.messaging.domain.model.CopilotHistoryEntry;
import com.riwi.messaging.domain.model.CopilotHistoryPage;
import com.riwi.messaging.domain.model.CopilotQueryRecord;
import com.riwi.messaging.domain.model.CopilotUsageRow;
import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.port.CopilotQueryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// adaptador de bitacora del copiloto: persistencia de consultas/citas y Consulta 4
@Repository
public class JdbcCopilotQueryRepository implements CopilotQueryRepository {

    private static final String INSERT_QUERY = """
            INSERT INTO rw_copilot_query
                (user_id, question, answer, model, prompt_tokens, completion_tokens, status, system_prompt_version)
            VALUES
                (:userId, :question, :answer, :model, :promptTokens, :completionTokens, :status, :promptVersion)
            RETURNING id
            """;

    private static final String INSERT_CITATION = """
            INSERT INTO rw_copilot_citation (query_id, message_id, rank)
            VALUES (:queryId, :messageId, :rank)
            """;

    // Consulta 4: consumo acumulado por usuario; el filtro por actor/admin vive en el WHERE
    private static final String USAGE_SQL = """
            SELECT
                q.user_id,
                p.display_name,
                p.job_title,
                count(*)                                                                        AS query_count,
                count(*) FILTER (WHERE q.status = 'answered')                                    AS answered_count,
                count(*) FILTER (WHERE q.status IN ('refused_no_context', 'refused_permission')) AS refused_count,
                count(*) FILTER (WHERE q.status = 'error')                                       AS error_count,
                coalesce(sum(q.prompt_tokens), 0)                                                AS prompt_tokens,
                coalesce(sum(q.completion_tokens), 0)                                            AS completion_tokens,
                coalesce(sum(q.total_tokens), 0)                                                 AS total_tokens,
                max(q.created_at)                                                                AS last_query_at
            FROM rw_copilot_query q
            JOIN rw_user_profile p ON p.user_id = q.user_id
            WHERE (CAST(:from AS timestamptz) IS NULL OR q.created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to   AS timestamptz) IS NULL OR q.created_at <  CAST(:to   AS timestamptz))
              AND (:isAdmin = true OR q.user_id = CAST(:actorId AS uuid))
              AND (CAST(:filterUserId AS uuid) IS NULL OR q.user_id = CAST(:filterUserId AS uuid))
            GROUP BY q.user_id, p.display_name, p.job_title
            ORDER BY total_tokens DESC, q.user_id
            """;

    private static final String HISTORY_SQL = """
            SELECT q.id, q.question, q.answer, q.model, q.status,
                   q.prompt_tokens, q.completion_tokens, q.total_tokens, q.created_at
            FROM rw_copilot_query q
            WHERE q.user_id = CAST(:actorId AS uuid)
              AND (
                    CAST(:beforeCreatedAt AS timestamptz) IS NULL
                    OR (q.created_at, q.id) < (CAST(:beforeCreatedAt AS timestamptz), CAST(:beforeId AS uuid))
                  )
            ORDER BY q.created_at DESC, q.id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCopilotQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID persist(CopilotQueryRecord record) {
        var params = new MapSqlParameterSource()
                .addValue("userId", record.userId())
                .addValue("question", record.question())
                .addValue("answer", record.answer())
                .addValue("model", record.model())
                .addValue("promptTokens", record.usage().promptTokens())
                .addValue("completionTokens", record.usage().completionTokens())
                .addValue("status", record.status().wire())
                .addValue("promptVersion", record.systemPromptVersion());

        // insertamos la consulta y recuperamos su id (rw_copilot_query no tiene RLS)
        UUID queryId = jdbc.queryForObject(INSERT_QUERY, params, UUID.class);

        if (!record.citations().isEmpty()) {
            // insertamos una cita por mensaje fuente, en lote parametrizado
            SqlParameterSource[] batch = record.citations().stream()
                    .map(citation -> (SqlParameterSource) new MapSqlParameterSource()
                            .addValue("queryId", queryId)
                            .addValue("messageId", citation.messageId())
                            .addValue("rank", citation.rank()))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate(INSERT_CITATION, batch);
        }
        return queryId;
    }

    @Override
    public List<CopilotUsageRow> usage(UUID actorId, boolean isPlatformAdmin, UUID filterUserId, Instant from, Instant to) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("isAdmin", isPlatformAdmin)
                .addValue("filterUserId", filterUserId)
                .addValue("from", from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("to", to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC));

        return jdbc.query(USAGE_SQL, params, usageMapper());
    }

    @Override
    public CopilotHistoryPage history(UUID actorId, Cursor before, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("beforeCreatedAt", before == null ? null
                        : OffsetDateTime.ofInstant(before.timestamp(), ZoneOffset.UTC))
                .addValue("beforeId", before == null ? null : before.id())
                // una fila extra para saber si hay pagina siguiente sin OFFSET
                .addValue("limit", limit + 1);

        List<CopilotHistoryEntry> rows = jdbc.query(HISTORY_SQL, params, historyMapper());

        boolean hasMore = rows.size() > limit;
        List<CopilotHistoryEntry> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        Cursor next = hasMore
                ? new Cursor(items.get(items.size() - 1).createdAt(), items.get(items.size() - 1).id())
                : null;
        return new CopilotHistoryPage(items, next);
    }

    private RowMapper<CopilotUsageRow> usageMapper() {
        return (ResultSet rs, int rowNum) -> {
            OffsetDateTime last = rs.getObject("last_query_at", OffsetDateTime.class);
            return new CopilotUsageRow(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("display_name"),
                    rs.getString("job_title"),
                    rs.getLong("query_count"),
                    rs.getLong("answered_count"),
                    rs.getLong("refused_count"),
                    rs.getLong("error_count"),
                    rs.getLong("prompt_tokens"),
                    rs.getLong("completion_tokens"),
                    rs.getLong("total_tokens"),
                    last == null ? null : last.toInstant());
        };
    }

    private RowMapper<CopilotHistoryEntry> historyMapper() {
        return (ResultSet rs, int rowNum) -> new CopilotHistoryEntry(
                rs.getObject("id", UUID.class),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getString("model"),
                rs.getString("status"),
                rs.getInt("prompt_tokens"),
                rs.getInt("completion_tokens"),
                rs.getInt("total_tokens"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
