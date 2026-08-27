package com.riwi.messaging.infrastructure.websocket;

import com.riwi.messaging.domain.exception.InvalidTokenException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.port.AccessTokenPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

// autentica el handshake del WebSocket con el mismo access token JWT de la API REST
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    // el navegador no permite cabeceras personalizadas al abrir un WebSocket => el token viaja como query param
    static final String TOKEN_PARAM = "access_token";
    static final String USER_ID_ATTRIBUTE = "userId";

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final AccessTokenPort accessTokens;

    public JwtHandshakeInterceptor(AccessTokenPort accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        // solo aceptamos handshakes servidos por el contenedor servlet
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter(TOKEN_PARAM);
        if (token == null || token.isBlank()) {
            log.debug("websocket handshake without access_token rejected");
            return false;
        }

        try {
            // el user_id sale EXCLUSIVAMENTE del claim sub del token verificado
            TokenClaims claims = accessTokens.verify(token);
            attributes.put(USER_ID_ATTRIBUTE, claims.userId());
            return true;
        } catch (InvalidTokenException e) {
            log.debug("websocket handshake with invalid access_token rejected");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // nada que hacer despues del handshake
    }
}
