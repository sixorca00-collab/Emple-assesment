package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.port.ConversationRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

// adaptador de lectura de conversaciones apoyado en la vista rw_user_conversation
@Repository
public class JdbcConversationRepository implements ConversationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcConversationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countForCurrentActor() {
        // la vista filtra por membresia del actor fijado en la transaccion (security_invoker + RLS)
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM rw_user_conversation",
                Map.of(),
                Long.class);
        return count == null ? 0L : count;
    }
}
