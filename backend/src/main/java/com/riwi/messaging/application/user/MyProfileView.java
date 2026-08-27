package com.riwi.messaging.application.user;

import java.util.UUID;

// resultado del caso de uso /me: perfil del actor + numero de conversaciones visibles (via RLS)
public record MyProfileView(
        UUID userId,
        String email,
        String displayName,
        String jobTitle,
        boolean platformAdmin,
        long visibleConversationCount
) {
}
