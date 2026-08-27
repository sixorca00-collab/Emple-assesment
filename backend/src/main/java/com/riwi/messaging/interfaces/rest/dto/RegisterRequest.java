package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// cuerpo de POST /auth/register; los limites reflejan los CHECK de rw_user_profile y la regla de 8+ del front
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @NotBlank @Size(min = 2, max = 80) String jobTitle,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
