package com.riwi.messaging.domain.model;

import java.util.UUID;

// contenido de negocio del access token; el userId siempre viene del claim sub
public record TokenClaims(
        UUID userId,
        boolean platformAdmin,
        String name,
        String jobTitle
) {
}
