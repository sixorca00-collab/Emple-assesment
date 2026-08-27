package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// refresh token persistido: solo se guarda su hash, nunca el valor en claro (rw_refresh_token)
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBy
) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    // vencido cuando el instante actual ya alcanzo expiresAt
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
