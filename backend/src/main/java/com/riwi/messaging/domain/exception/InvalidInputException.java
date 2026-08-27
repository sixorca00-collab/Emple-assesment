package com.riwi.messaging.domain.exception;

// la entrada del cliente no es valida para la operacion (se mapea a HTTP 400)
public class InvalidInputException extends DomainException {

    public InvalidInputException(String message) {
        super("INVALID_INPUT", message);
    }
}
