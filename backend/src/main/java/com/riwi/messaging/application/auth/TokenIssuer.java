package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.domain.model.RefreshToken;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.port.AccessTokenPort;
import com.riwi.messaging.domain.port.OpaqueTokenGenerator;
import com.riwi.messaging.domain.port.RefreshTokenRepository;
import com.riwi.messaging.domain.port.TokenHasher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// emite un par access+refresh y persiste el hash del refresh; lo comparten login y refresh
@Component
public class TokenIssuer {

    private final AccessTokenPort accessTokens;
    private final OpaqueTokenGenerator opaqueTokens;
    private final TokenHasher tokenHasher;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenPolicy policy;
    private final Clock clock;

    public TokenIssuer(AccessTokenPort accessTokens,
                       OpaqueTokenGenerator opaqueTokens,
                       TokenHasher tokenHasher,
                       RefreshTokenRepository refreshTokens,
                       RefreshTokenPolicy policy,
                       Clock clock) {
        this.accessTokens = accessTokens;
        this.opaqueTokens = opaqueTokens;
        this.tokenHasher = tokenHasher;
        this.refreshTokens = refreshTokens;
        this.policy = policy;
        this.clock = clock;
    }

    IssuedPair issueFor(TokenClaims claims) {
        // firmamos el access token con los claims de negocio
        AccessTokenPort.IssuedAccessToken access = accessTokens.issue(claims);

        // generamos el refresh opaco y persistimos solo su hash
        String rawRefresh = opaqueTokens.generate();
        Instant now = clock.instant();
        UUID refreshId = UUID.randomUUID();
        RefreshToken toStore = new RefreshToken(
                refreshId,
                claims.userId(),
                tokenHasher.hash(rawRefresh),
                now,
                now.plus(policy.timeToLive()),
                null,
                null);
        refreshTokens.save(toStore);

        return new IssuedPair(new AuthTokens(access.value(), rawRefresh, access.expiresAt()), refreshId);
    }

    // par emitido junto con el id del refresh recien creado (necesario para enlazar la rotacion)
    record IssuedPair(AuthTokens tokens, UUID refreshTokenId) {
    }
}
