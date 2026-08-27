package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// proyeccion publica de un usuario para la gestion admin; refleja lo que devuelve rw_search_users
public record UserSummary(
        UUID id,
        String displayName,
        String jobTitle,
        String avatarUrl,
        boolean active,
        Instant createdAt
) {
}
