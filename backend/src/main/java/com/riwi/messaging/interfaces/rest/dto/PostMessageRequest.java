package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// cuerpo para publicar un mensaje; clientNonce (uuid) es opcional y sirve para deduplicar reenvios
public record PostMessageRequest(
        @NotBlank @Size(min = 1, max = 8000) String body,
        UUID clientNonce
) {
}
