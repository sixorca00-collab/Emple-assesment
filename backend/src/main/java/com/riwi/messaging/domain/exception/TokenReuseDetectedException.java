package com.riwi.messaging.domain.exception;

// se presento un refresh ya revocado: senal de robo, se revoca toda la cadena del usuario
public class TokenReuseDetectedException extends DomainException {

    public TokenReuseDetectedException() {
        super("AUTH_TOKEN_REUSE", "Refresh token reuse detected; all sessions were revoked");
    }
}
