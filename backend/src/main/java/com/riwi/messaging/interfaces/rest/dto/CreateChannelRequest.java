package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// cuerpo para crear un canal
public record CreateChannelRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @Size(max = 500) String description,
        boolean isPrivate
) {
}
