package com.riwi.messaging.domain.exception;

// la operacion es invalida para el estado actual del recurso (se mapea a HTTP 409)
public class InvalidStateException extends DomainException {

    public InvalidStateException(String message) {
        super("CONFLICT", message);
    }
}
