package com.riwi.messaging.interfaces.rest.dto;

// respuesta con el par de tokens; el refresh se entrega una unica vez
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
