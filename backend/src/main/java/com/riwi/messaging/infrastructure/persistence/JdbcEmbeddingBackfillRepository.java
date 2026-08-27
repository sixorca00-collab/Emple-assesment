package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.EmbeddingCoverage;
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
    public List<PendingMessage> allLive(int limit) {
        var params = new MapSqlParameterSource("limit", limit);
        // funcion SECURITY DEFINER: lista TODOS los mensajes vivos para re-embedding total
        return jdbc.query(
                "SELECT message_id, body FROM rw_messages_for_reembedding(:limit)",
                params,
                pendingMapper());
    }

    @Override
    public EmbeddingCoverage coverage() {
        // funcion SECURITY DEFINER: conteo global de cobertura de embeddings
        return jdbc.queryForObject(
                "SELECT total_messages, messages_with_embedding FROM rw_message_embedding_stats()",
                new MapSqlParameterSource(),
                (ResultSet rs, int rowNum) -> new EmbeddingCoverage(
                        rs.getLong("total_messages"),
                        rs.getLong("messages_with_embedding")));
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
