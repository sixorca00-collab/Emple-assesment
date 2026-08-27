package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.domain.exception.AuthenticationFailedException;
import com.riwi.messaging.domain.exception.DomainException;
import com.riwi.messaging.domain.exception.InvalidTokenException;
import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import com.riwi.messaging.domain.exception.TokenReuseDetectedException;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

// traduce excepciones a un cuerpo de error consistente con codigo HTTP correcto
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AuthenticationFailedException.class, InvalidTokenException.class, TokenReuseDetectedException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(DomainException ex) {
        // credenciales o token invalido: 401 sin filtrar el detalle
        return build(HttpStatus.UNAUTHORIZED, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // tomamos el primer error de campo como mensaje legible
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // no filtramos el detalle interno al cliente, pero si lo registramos
        log.error("unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        var body = new ErrorResponse(code, message, MDC.get(CorrelationIdFilter.MDC_KEY), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
