package com.riwi.messaging.application.user;

import java.util.UUID;

// entrada del caso de uso de edicion; los campos nulos dejan el valor actual (regla del SP)
public record UpdateUserCommand(
        UUID targetId,
        String displayName,
        String jobTitle,
        String avatarUrl,
        String bio,
        Boolean active
) {
}
