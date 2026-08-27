package com.riwi.messaging.domain.model;

// estado de cableado del copiloto: cobertura de embeddings + alcance real de cada proveedor de IA
public record CopilotReadiness(
        long totalMessages,
        long messagesWithEmbedding,
        String embeddingModel,
        String chatModel,
        boolean embeddingProviderReachable,
        boolean chatProviderReachable
) {
}
