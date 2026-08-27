package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// un canal visible para el actor
public record ChannelView(
        UUID id,
        String name,
        String description,
        boolean isPrivate,
        String myRole,
        Instant createdAt
) {
}
