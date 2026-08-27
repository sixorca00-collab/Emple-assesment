package com.riwi.messaging.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// registro en memoria de sesiones WebSocket abiertas, indexadas por usuario autenticado
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    // registra la sesion recien abierta para su usuario
    public void register(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    // quita la sesion cerrada y limpia la entrada si el usuario ya no tiene sesiones
    public void unregister(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    // sesiones abiertas de un usuario (vacio si no esta conectado)
    public Set<WebSocketSession> sessionsOf(UUID userId) {
        return sessionsByUser.getOrDefault(userId, Set.of());
    }
}
