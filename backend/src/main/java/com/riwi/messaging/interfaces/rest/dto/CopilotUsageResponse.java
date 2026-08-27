package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.CopilotUsageRow;

import java.time.Instant;
import java.util.UUID;

// fila del reporte de consumo del copiloto (Consulta 4)
public record CopilotUsageResponse(
        UUID userId,
        String displayName,
        String jobTitle,
        long queryCount,
        long answeredCount,
        long refusedCount,
        long errorCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        Instant lastQueryAt
) {

    public static CopilotUsageResponse from(CopilotUsageRow row) {
        return new CopilotUsageResponse(
                row.userId(), row.displayName(), row.jobTitle(),
                row.queryCount(), row.answeredCount(), row.refusedCount(), row.errorCount(),
                row.promptTokens(), row.completionTokens(), row.totalTokens(), row.lastQueryAt());
    }
}
