package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.CopilotHistoryEntry;

import java.time.Instant;
import java.util.UUID;

// entrada del historial de consultas del copiloto del propio actor
public record CopilotHistoryResponse(
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

    public static CopilotHistoryResponse from(CopilotHistoryEntry entry) {
        return new CopilotHistoryResponse(
                entry.id(), entry.question(), entry.answer(), entry.model(), entry.status(),
                entry.promptTokens(), entry.completionTokens(), entry.totalTokens(), entry.createdAt());
    }
}
