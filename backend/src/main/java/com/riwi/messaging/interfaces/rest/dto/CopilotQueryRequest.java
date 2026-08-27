package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// entrada del copiloto; solo la pregunta, el actor sale del JWT
public record CopilotQueryRequest(
        @NotBlank
        @Size(max = 4000)
        String question
) {
}
