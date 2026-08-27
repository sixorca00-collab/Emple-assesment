package com.riwi.messaging.domain.model;

import java.util.UUID;

// perfil visible del actor autenticado (rw_user + rw_user_profile)
public record ActorProfile(
        UUID userId,
        String email,
        String displayName,
        String jobTitle,
        boolean platformAdmin
) {
}
