package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.PendingMessage;
import com.riwi.messaging.domain.port.EmbeddingBackfillRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

// adaptador de backfill de embeddings: usa las funciones SECURITY DEFINER de V5
@Repository
public class JdbcEmbeddingBackfillRepository implements EmbeddingBackfillRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEmbeddingBackfillRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PendingMessage> pending(int limit) {
        var params = new MapSqlParameterSource("limit", limit);
        // funcion SECURITY DEFINER: lista mensajes sin embedding de todos los canales
        return jdbc.query(
                "SELECT message_id, body FROM rw_messages_missing_embedding(:limit)",
                params,
                pendingMapper());
    }

    @Override
    public void saveEmbedding(UUID messageId, float[] embedding) {
        var params = new MapSqlParameterSource()
                .addValue("messageId", messageId)
                .addValue("embedding", VectorLiterals.toLiteral(embedding));
        // UPDATE parametrizado vía funcion SECURITY DEFINER (la RLS de UPDATE es por autoria)
        jdbc.query(
                "SELECT rw_set_message_embedding(CAST(:messageId AS uuid), CAST(:embedding AS vector))",
                params,
                (ResultSet rs) -> null);
    }

    private RowMapper<PendingMessage> pendingMapper() {
        return (ResultSet rs, int rowNum) -> new PendingMessage(
                rs.getObject("message_id", UUID.class),
                rs.getString("body"));
    }
}
