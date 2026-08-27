package com.riwi.messaging.domain.model;

// cobertura de embeddings sobre el corpus vivo; alimenta el readiness del copiloto
public record EmbeddingCoverage(
        long totalMessages,
        long messagesWithEmbedding
) {
}
