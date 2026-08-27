package com.riwi.messaging.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.messaging.domain.model.MessageBroadcast;
import com.riwi.messaging.domain.port.MessageBroadcastPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

// adaptador de tiempo real: emite el evento a las sesiones de los miembros del canal
@Component
public class WebSocketMessageBroadcaster implements MessageBroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessageBroadcaster.class);

    private final WebSocketSessionRegistry registry;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WebSocketMessageBroadcaster(WebSocketSessionRegistry registry,
                                       JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper) {
        this.registry = registry;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(MessageBroadcast event) {
        UUID channelId = event.message().channelId();

        // la lista de destinatarios se resuelve en la BD, no se confia en el cliente
        List<UUID> memberIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM rw_channel_member WHERE channel_id = ?", UUID.class, channelId);

        String frame = serialize(event);
        if (frame == null) {
            return;
        }

        // enviamos solo a los miembros del canal que tengan una sesion abierta
        for (UUID memberId : memberIds) {
            for (WebSocketSession session : registry.sessionsOf(memberId)) {
                sendQuietly(session, frame);
            }
        }
    }

    private String serialize(MessageBroadcast event) {
        try {
            return objectMapper.writeValueAsString(MessageEventPayload.created(event.message()));
        } catch (IOException e) {
            log.error("failed to serialize websocket message event", e);
            return null;
        }
    }

    private void sendQuietly(WebSocketSession session, String frame) {
        try {
            // un fallo de envio a un cliente no debe afectar al resto
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(frame));
            }
        } catch (IOException e) {
            log.warn("failed to push websocket event to session {}", session.getId(), e);
        }
    }
}
