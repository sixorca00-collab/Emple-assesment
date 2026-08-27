package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.RetrievedMessage;
import com.riwi.messaging.domain.port.CopilotContextRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// adaptador de la Consulta 3: recuperacion vectorial con permisos aplicados EN SQL + RLS
@Repository
public class JdbcCopilotContextRepository implements CopilotContextRepository {

    // igual que db/queries/03: EXISTS contra rw_channel_member exige que el actor sea miembro
    private static final String RETRIEVE_SQL = """
            SELECT
                m.id            AS message_id,
                m.channel_id,
                c.name          AS channel_name,
                m.sender_id,
                p.display_name  AS author_name,
                p.job_title     AS author_job_title,
                m.body,
                m.created_at,
                1 - (m.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM rw_message m
            JOIN rw_channel c      ON c.id = m.channel_id
            JOIN rw_user_profile p ON p.user_id = m.sender_id
            WHERE m.deleted_at IS NULL
              AND m.embedding IS NOT NULL
              AND EXISTS (
                    SELECT 1
                    FROM rw_channel_member cm
                    WHERE cm.channel_id = m.channel_id
                      AND cm.user_id = CAST(:actorId AS uuid)
                  )
              AND (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) >= CAST(:minSimilarity AS real)
            ORDER BY m.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :matchCount
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCopilotContextRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetrievedMessage> retrieveForActor(UUID actorId, float[] queryEmbedding, int matchCount, double minSimilarity) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("queryEmbedding", VectorLiterals.toLiteral(queryEmbedding))
                .addValue("minSimilarity", (float) minSimilarity)
                .addValue("matchCount", matchCount);

        // la RLS p_rw_message_select vuelve a filtrar por membresia (defensa en profundidad)
        return jdbc.query(RETRIEVE_SQL, params, contextMapper());
    }

    @Override
    public boolean contextExistsAnywhere(float[] queryEmbedding, double minSimilarity) {
        var params = new MapSqlParameterSource()
                .addValue("queryEmbedding", VectorLiterals.toLiteral(queryEmbedding))
                .addValue("minSimilarity", (float) minSimilarity);

        // funcion SECURITY DEFINER: solo dice si el contexto existe, nunca devuelve contenido
        Boolean exists = jdbc.queryForObject(
                "SELECT rw_copilot_context_exists_elsewhere(CAST(:queryEmbedding AS vector), CAST(:minSimilarity AS real))",
                params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private RowMapper<RetrievedMessage> contextMapper() {
        return (ResultSet rs, int rowNum) -> new RetrievedMessage(
                rs.getObject("message_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getString("channel_name"),
                rs.getObject("sender_id", UUID.class),
                rs.getString("author_name"),
                rs.getString("author_job_title"),
                rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getDouble("similarity"));
    }
}
