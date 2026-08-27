package com.riwi.messaging.domain.exception;

// error de negocio con un codigo estable para el cuerpo de error de la API
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
