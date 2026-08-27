package com.riwi.messaging.domain.model;

import java.time.Instant;

// par de tokens emitido tras login o rotacion; el refresh se entrega en claro una unica vez
public record AuthTokens(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt
) {
}
