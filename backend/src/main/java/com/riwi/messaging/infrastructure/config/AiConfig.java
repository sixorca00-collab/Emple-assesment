package com.riwi.messaging.infrastructure.config;

import com.riwi.messaging.application.copilot.CopilotModels;
import com.riwi.messaging.application.copilot.CopilotPromptBuilder;
import com.riwi.messaging.application.copilot.CopilotSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// cablea la config de IA/copiloto (toda por entorno) hacia la capa de aplicacion
@Configuration
@EnableConfigurationProperties({AiChatProperties.class, AiEmbeddingProperties.class, CopilotProperties.class})
public class AiConfig {

    // la aplicacion solo conoce los ajustes de recuperacion, no la fuente de config
    @Bean
    CopilotSettings copilotSettings(CopilotProperties properties) {
        return new CopilotSettings(properties.topK(), properties.minSimilarity(), CopilotPromptBuilder.VERSION);
    }

    // los ids de modelo llegan a la aplicacion como valor, sin acoplarla a las properties de infraestructura
    @Bean
    CopilotModels copilotModels(AiChatProperties chat, AiEmbeddingProperties embedding) {
        return new CopilotModels(chat.model(), embedding.model());
    }
}
