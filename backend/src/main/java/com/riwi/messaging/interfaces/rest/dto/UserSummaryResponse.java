package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.UserSummary;

import java.time.Instant;
import java.util.UUID;

// respuesta de un usuario en el listado admin
public record UserSummaryResponse(
        UUID id,
        String displayName,
        String jobTitle,
        String avatarUrl,
        boolean isActive,
        Instant createdAt
) {

    public static UserSummaryResponse from(UserSummary user) {
        return new UserSummaryResponse(
                user.id(), user.displayName(), user.jobTitle(),
                user.avatarUrl(), user.active(), user.createdAt());
    }
}
