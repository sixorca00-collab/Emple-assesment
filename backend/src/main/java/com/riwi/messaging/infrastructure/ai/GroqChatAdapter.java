package com.riwi.messaging.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riwi.messaging.domain.port.ChatPort;
import com.riwi.messaging.infrastructure.config.AiChatProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// adaptador HTTP del ChatPort contra Groq (endpoint compatible con OpenAI); sin SDK de terceros
@Component
public class GroqChatAdapter implements ChatPort {

    private final RestClient client;
    private final String model;

    public GroqChatAdapter(AiChatProperties properties) {
        // base url y api key vienen 100% de entorno (AI_CHAT_BASE_URL / AI_CHAT_API_KEY)
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
        this.model = properties.model();
    }

    @Override
    public ChatResult complete(String systemPrompt, String userPrompt) {
        // cuerpo con formato chat/completions de OpenAI: turno system + turno user
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        // llamamos al endpoint de chat del proveedor
        ChatCompletion response = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatCompletion.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("empty chat completion response");
        }

        String content = response.choices().get(0).message().content();
        int promptTokens = response.usage() == null ? 0 : response.usage().promptTokens();
        int completionTokens = response.usage() == null ? 0 : response.usage().completionTokens();
        String usedModel = response.model() == null ? model : response.model();
        return new ChatResult(content, promptTokens, completionTokens, usedModel);
    }

    // proyeccion minima de la respuesta del proveedor
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletion(String model, List<Choice> choices, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String role, String content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Usage(
                @JsonProperty("prompt_tokens") int promptTokens,
                @JsonProperty("completion_tokens") int completionTokens) {
        }
    }
}
