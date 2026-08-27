package com.riwi.messaging.infrastructure.security;

import com.riwi.messaging.domain.exception.InvalidTokenException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.port.AccessTokenPort;
import com.riwi.messaging.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// adaptador jjwt: firma HS256 y valida el access token
@Component
public class JjwtAccessTokenAdapter implements AccessTokenPort {

    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final Duration timeToLive;
    private final Clock clock;

    public JjwtAccessTokenAdapter(JwtProperties properties, Clock clock) {
        // HS256 exige una clave de al menos 256 bits (32 caracteres)
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.timeToLive = Duration.ofMinutes(properties.accessExpirationMinutes());
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(TokenClaims claims) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(timeToLive);

        // sub = user_id; el copiloto lee name/job_title desde estos claims
        String jwt = Jwts.builder()
                .subject(claims.userId().toString())
                .claim("is_platform_admin", claims.platformAdmin())
                .claim("name", claims.name())
                .claim("job_title", claims.jobTitle())
                .claim("type", TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(jwt, expiresAt);
    }

    @Override
    public TokenClaims verify(String token) {
        try {
            // valida firma y expiracion
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Claims body = parsed.getPayload();

            // rechazamos tokens que no sean de tipo access (p.ej. un refresh manipulado)
            if (!TYPE_ACCESS.equals(body.get("type", String.class))) {
                throw new InvalidTokenException("Not an access token");
            }

            return new TokenClaims(
                    UUID.fromString(body.getSubject()),
                    Boolean.TRUE.equals(body.get("is_platform_admin", Boolean.class)),
                    body.get("name", String.class),
                    body.get("job_title", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid access token");
        }
    }
}
