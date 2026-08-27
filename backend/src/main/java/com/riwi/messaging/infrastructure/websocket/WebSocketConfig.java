package com.riwi.messaging.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// registra el endpoint WebSocket plano /ws/messages con autenticacion de handshake por JWT
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(MessageWebSocketHandler handler, JwtHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // el interceptor rechaza el handshake si el access token falta o es invalido
        registry.addHandler(handler, "/ws/messages")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
