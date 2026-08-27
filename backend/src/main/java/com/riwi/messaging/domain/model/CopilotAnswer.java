package com.riwi.messaging.domain.model;

import java.util.List;

// resultado del caso de uso del copiloto: respuesta, estado, citas y consumo
public record CopilotAnswer(
        String answer,
        CopilotStatus status,
        List<CopilotCitation> citations,
        CopilotTokenUsage usage
) {
}
