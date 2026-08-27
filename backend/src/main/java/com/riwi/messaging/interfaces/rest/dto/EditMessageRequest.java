package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// cuerpo para editar un mensaje
public record EditMessageRequest(
        @NotBlank @Size(min = 1, max = 8000) String body
) {
}
