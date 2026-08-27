package com.riwi.messaging.support;

import com.riwi.messaging.domain.port.EmbeddingPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// reemplaza los adaptadores de Groq/OpenAI por dobles deterministas; nunca se llama a una API externa
@TestConfiguration
public class FakeAiConfig {

    // 1536 dimensiones, igual que rw_message.embedding
    @Bean
    @Primary
    EmbeddingPort fakeEmbeddingPort() {
        return new DeterministicEmbeddingPort(1536);
    }

    // primary tambien para inyecciones de ChatPort (RecordingChatPort lo implementa)
    @Bean
    @Primary
    RecordingChatPort fakeChatPort() {
        return new RecordingChatPort();
    }
}
