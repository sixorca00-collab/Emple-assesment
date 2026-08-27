package com.riwi.messaging.interfaces.rest.dto;

import java.time.Instant;

// cuerpo de error uniforme para toda la API
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp
) {
}
