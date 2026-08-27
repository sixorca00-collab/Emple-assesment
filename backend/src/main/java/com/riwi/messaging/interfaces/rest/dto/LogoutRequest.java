package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

// cuerpo de POST /auth/logout
public record LogoutRequest(
        @NotBlank String refreshToken
) {
}
