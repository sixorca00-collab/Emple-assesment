package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// puerto de persistencia de refresh tokens con soporte de rotacion y revocacion en cadena
public interface RefreshTokenRepository {

    void save(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    // marca el token rotado: lo revoca y enlaza con su reemplazo
    void markRotated(UUID tokenId, UUID replacementId, Instant when);

    void revoke(UUID tokenId, Instant when);

    // revoca todos los tokens vigentes del usuario (defensa ante reuso/robo)
    int revokeAllActiveForUser(UUID userId, Instant when);
}
