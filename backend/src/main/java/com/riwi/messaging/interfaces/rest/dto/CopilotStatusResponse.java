package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.CopilotReadiness;

// readiness del copiloto para verificacion previa a la demo
public record CopilotStatusResponse(
        long totalMessages,
        long messagesWithEmbedding,
        String embeddingModel,
        String chatModel,
        boolean embeddingProviderReachable,
        boolean chatProviderReachable
) {

    public static CopilotStatusResponse from(CopilotReadiness readiness) {
        return new CopilotStatusResponse(
                readiness.totalMessages(),
                readiness.messagesWithEmbedding(),
                readiness.embeddingModel(),
                readiness.chatModel(),
                readiness.embeddingProviderReachable(),
                readiness.chatProviderReachable());
    }
}
