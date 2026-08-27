package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// fila del reporte de consumo acumulado del copiloto por usuario (Consulta 4)
public record CopilotUsageRow(
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
}
