package com.riwi.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// config del proveedor de chat del copiloto (Groq, formato OpenAI); 100% por entorno
@ConfigurationProperties(prefix = "riwi.ai.chat")
public record AiChatProperties(
        String baseUrl,
        String apiKey,
        String model
) {
}
