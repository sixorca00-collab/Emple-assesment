package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

// cuerpo de POST /auth/refresh
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
