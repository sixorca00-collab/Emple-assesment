package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// cuerpo de POST /auth/login
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
