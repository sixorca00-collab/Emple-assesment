package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.exception.InvalidStateException;
import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

// traduce los SQLSTATE que levantan las funciones de la BD a excepciones de dominio
final class DbFunctionErrors {

    private DbFunctionErrors() {
    }

    // ejecuta el trabajo contra la BD y reempaqueta los errores conocidos de las funciones
    static <T> T mapping(java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (DataAccessException ex) {
            throw translate(ex);
        }
    }

    private static RuntimeException translate(DataAccessException ex) {
        String state = sqlState(ex);
        String message = rootMessage(ex);
        return switch (state == null ? "" : state) {
            // 42501 = permiso denegado; 28000 = no hay actor autenticado fijado
            case "42501", "28000" -> new NotAuthorizedException(message);
            // P0002 = no encontrado o no visible por RLS
            case "P0002" -> new ResourceNotFoundException(message);
            // 55000 = estado invalido para la operacion (p.ej. editar un mensaje ya borrado)
            case "55000" -> new InvalidStateException(message);
            default -> ex;
        };
    }

    private static String sqlState(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
    }

    private static String rootMessage(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        // el driver antepone "ERROR: " al mensaje de la funcion; lo recortamos
        String raw = cause.getMessage() == null ? ex.getMessage() : cause.getMessage();
        int newline = raw.indexOf('\n');
        String firstLine = newline >= 0 ? raw.substring(0, newline) : raw;
        return firstLine.replaceFirst("^ERROR:\\s*", "");
    }
}
