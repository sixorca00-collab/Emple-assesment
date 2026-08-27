package com.riwi.messaging.domain.exception;

// el recurso solicitado no existe o no es visible para el actor
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
