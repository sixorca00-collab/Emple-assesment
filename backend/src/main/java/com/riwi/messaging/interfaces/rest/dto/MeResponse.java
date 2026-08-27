package com.riwi.messaging.interfaces.rest.dto;

// respuesta de GET /me: perfil del actor autenticado
public record MeResponse(
        String email,
        String displayName,
        String jobTitle,
        boolean platformAdmin,
        long visibleConversationCount
) {
}
