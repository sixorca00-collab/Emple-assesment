package com.riwi.messaging.domain.exception;

// token de acceso o refresh invalido, desconocido o vencido
public class InvalidTokenException extends DomainException {

    public InvalidTokenException(String message) {
        super("AUTH_INVALID_TOKEN", message);
    }
}
