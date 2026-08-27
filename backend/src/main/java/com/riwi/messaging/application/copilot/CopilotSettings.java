package com.riwi.messaging.application.copilot;

// ajustes del copiloto tomados de configuracion (nunca hardcodeados en el caso de uso)
public record CopilotSettings(
        int topK,
        double minSimilarity,
        String systemPromptVersion
) {
}
