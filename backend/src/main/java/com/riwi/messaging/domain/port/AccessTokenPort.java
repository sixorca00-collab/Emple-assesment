package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.TokenClaims;

import java.time.Instant;

// puerto de emision/verificacion del access token; el adaptador usa jjwt (HS256)
public interface AccessTokenPort {

    IssuedAccessToken issue(TokenClaims claims);

    // verifica firma, expiracion y type=access; devuelve los claims de negocio
    TokenClaims verify(String token);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
