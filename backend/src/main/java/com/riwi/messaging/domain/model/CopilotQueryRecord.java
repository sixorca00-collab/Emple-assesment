package com.riwi.messaging.domain.model;

import java.util.List;
import java.util.UUID;

// datos a persistir de una consulta del copiloto (fila de rw_copilot_query + sus citas)
public record CopilotQueryRecord(
        UUID userId,
        String question,
        String answer,
        String model,
        CopilotStatus status,
        String systemPromptVersion,
        CopilotTokenUsage usage,
        List<CopilotCitation> citations
) {
}
