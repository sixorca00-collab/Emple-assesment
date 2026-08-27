package com.riwi.messaging.domain.exception;

// login fallido; mensaje generico para no revelar si fallo el correo o la contrasena
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException() {
        super("AUTH_INVALID_CREDENTIALS", "Invalid email or password");
    }
}
