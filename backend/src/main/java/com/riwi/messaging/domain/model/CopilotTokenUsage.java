package com.riwi.messaging.domain.model;

// consumo de tokens de una consulta al copiloto
public record CopilotTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {

    // total derivado, igual que la columna GENERATED de rw_copilot_query
    public static CopilotTokenUsage of(int promptTokens, int completionTokens) {
        return new CopilotTokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }

    public static CopilotTokenUsage none() {
        return new CopilotTokenUsage(0, 0, 0);
    }
}
