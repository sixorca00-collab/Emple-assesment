package com.riwi.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// ajustes de recuperacion del copiloto RAG
@ConfigurationProperties(prefix = "riwi.copilot")
public record CopilotProperties(
        int topK,
        double minSimilarity
) {
}
