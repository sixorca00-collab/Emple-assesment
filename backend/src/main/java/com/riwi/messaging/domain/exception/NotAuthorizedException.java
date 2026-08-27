package com.riwi.messaging.domain.exception;

// el actor esta autenticado pero no tiene permiso sobre el recurso (se mapea a HTTP 403)
public class NotAuthorizedException extends DomainException {

    public NotAuthorizedException(String message) {
        super("FORBIDDEN", message);
    }
}
