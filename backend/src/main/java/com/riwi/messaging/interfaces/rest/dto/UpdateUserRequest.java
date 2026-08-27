package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.Size;

// cuerpo del PATCH /users/{id}; todo opcional, los nulos dejan el valor actual (regla del SP)
public record UpdateUserRequest(
        @Size(min = 2, max = 80) String displayName,
        @Size(min = 2, max = 80) String jobTitle,
        @Size(max = 500) String avatarUrl,
        @Size(max = 2000) String bio,
        Boolean isActive
) {
}
