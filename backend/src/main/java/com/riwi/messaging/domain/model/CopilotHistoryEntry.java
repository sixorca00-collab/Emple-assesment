package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// entrada del historial de consultas del copiloto del propio actor
public record CopilotHistoryEntry(
        UUID id,
        String question,
        String answer,
        String model,
        String status,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        Instant createdAt
) {
}
