package com.riwi.messaging.interfaces.rest.support;

// el cursor de paginacion recibido no es decodificable (se mapea a HTTP 400)
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String message) {
        super(message);
    }
}
