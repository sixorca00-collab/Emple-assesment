package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.port.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

// revoca todas las sesiones de un usuario en su propia transaccion
@Component
public class TokenReuseGuard {

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenReuseGuard(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    // REQUIRES_NEW: la revocacion se confirma aunque el caso de uso luego rechace la peticion con una excepcion
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(UUID userId) {
        refreshTokens.revokeAllActiveForUser(userId, clock.instant());
    }
}
