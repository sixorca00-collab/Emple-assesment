package com.riwi.messaging.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

// handler WebSocket plano: solo administra el ciclo de vida de las sesiones por usuario
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;

    public MessageWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // el userId lo dejo el handshake interceptor tras verificar el JWT
        registry.register(currentUser(session), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(currentUser(session), session);
    }

    private UUID currentUser(WebSocketSession session) {
        return (UUID) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
    }
}
