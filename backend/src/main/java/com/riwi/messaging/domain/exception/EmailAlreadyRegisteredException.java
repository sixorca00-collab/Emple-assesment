package com.riwi.messaging.domain.exception;

// el correo ya pertenece a un usuario vigente; se mapea a HTTP 409
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        super("AUTH_EMAIL_TAKEN", "Email is already registered");
    }
}
