package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.exception.InvalidTokenException;
import com.riwi.messaging.domain.exception.TokenReuseDetectedException;
import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.domain.model.RefreshToken;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.model.User;
import com.riwi.messaging.domain.port.RefreshTokenRepository;
import com.riwi.messaging.domain.port.TokenHasher;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// caso de uso: valida el refresh actual, lo rota y emite un par nuevo
@Service
public class RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final TokenHasher tokenHasher;
    private final UserRepository users;
    private final TokenIssuer tokenIssuer;
    private final TokenReuseGuard tokenReuseGuard;
    private final Clock clock;

    public RefreshTokenUseCase(RefreshTokenRepository refreshTokens,
                               TokenHasher tokenHasher,
                               UserRepository users,
                               TokenIssuer tokenIssuer,
                               TokenReuseGuard tokenReuseGuard,
                               Clock clock) {
        this.refreshTokens = refreshTokens;
        this.tokenHasher = tokenHasher;
        this.users = users;
        this.tokenIssuer = tokenIssuer;
        this.tokenReuseGuard = tokenReuseGuard;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens execute(RefreshCommand command) {
        Instant now = clock.instant();

        // buscamos por hash; el valor en claro nunca se persiste
        RefreshToken current = refreshTokens.findByHash(tokenHasher.hash(command.rawRefreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh token"));

        // reuso de un token ya revocado => posible robo: revocamos toda la cadena en su propia transaccion
        if (current.isRevoked()) {
            tokenReuseGuard.revokeAllSessions(current.userId());
            throw new TokenReuseDetectedException();
        }

        if (current.isExpired(now)) {
            throw new InvalidTokenException("Refresh token expired");
        }

        // el usuario debe seguir activo
        User user = users.findById(current.userId())
                .filter(User::canAuthenticate)
                .orElseThrow(() -> new InvalidTokenException("User is no longer active"));

        ActorProfile profile = users.findProfileById(user.id())
                .orElseThrow(() -> new InvalidTokenException("User profile is missing"));

        TokenClaims claims = new TokenClaims(
                user.id(),
                user.platformAdmin(),
                profile.displayName(),
                profile.jobTitle());

        // emitimos el par nuevo y enlazamos la rotacion (revoca el anterior + replaced_by)
        TokenIssuer.IssuedPair issued = tokenIssuer.issueFor(claims);
        refreshTokens.markRotated(current.id(), issued.refreshTokenId(), now);

        return issued.tokens();
    }
}
