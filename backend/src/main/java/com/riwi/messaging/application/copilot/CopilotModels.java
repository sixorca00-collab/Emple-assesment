package com.riwi.messaging.application.copilot;

// identificadores de modelo de IA en uso, inyectados desde configuracion (nunca hardcodeados)
public record CopilotModels(
        String chatModel,
        String embeddingModel
) {
}
