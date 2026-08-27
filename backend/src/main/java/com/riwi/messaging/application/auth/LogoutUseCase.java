package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.model.RefreshToken;
import com.riwi.messaging.domain.port.RefreshTokenRepository;
import com.riwi.messaging.domain.port.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// caso de uso: revoca el refresh token presentado (cierre de sesion)
@Service
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    public LogoutUseCase(RefreshTokenRepository refreshTokens, TokenHasher tokenHasher, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Transactional
    public void execute(LogoutCommand command) {
        // idempotente: si el token no existe o ya estaba revocado, no hacemos nada
        refreshTokens.findByHash(tokenHasher.hash(command.rawRefreshToken()))
                .filter(token -> !token.isRevoked())
                .map(RefreshToken::id)
                .ifPresent(id -> refreshTokens.revoke(id, clock.instant()));
    }
}
