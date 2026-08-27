package com.riwi.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// config del proveedor de embeddings (OpenAI por defecto); dimensions debe casar con vector(1536)
@ConfigurationProperties(prefix = "riwi.ai.embedding")
public record AiEmbeddingProperties(
        String baseUrl,
        String apiKey,
        String model,
        int dimensions
) {
}
