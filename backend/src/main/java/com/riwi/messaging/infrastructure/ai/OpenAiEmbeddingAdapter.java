package com.riwi.messaging.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riwi.messaging.domain.port.EmbeddingPort;
import com.riwi.messaging.infrastructure.config.AiEmbeddingProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// adaptador HTTP del EmbeddingPort contra OpenAI (Groq no expone embeddings); sin SDK de terceros
@Component
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final RestClient client;
    private final String model;
    private final int dimensions;

    public OpenAiEmbeddingAdapter(AiEmbeddingProperties properties, RestClient.Builder builder) {
        // base url y api key vienen 100% de entorno (AI_EMBEDDING_BASE_URL / AI_EMBEDDING_API_KEY)
        this.client = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
        this.model = properties.model();
        this.dimensions = properties.dimensions();
    }

    @Override
    public float[] embed(String text) {
        // cuerpo con formato /embeddings de OpenAI; dimensions fuerza el tamano del vector (Gemini devolveria 3072 sin esto)
        Map<String, Object> body = Map.of("model", model, "input", text, "dimensions", dimensions);

        // llamamos al endpoint de embeddings del proveedor
        EmbeddingResponse response = client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("empty embedding response");
        }

        List<Double> vector = response.data().get(0).embedding();
        if (vector.size() != dimensions) {
            throw new IllegalStateException("embedding dimension mismatch: expected " + dimensions + " got " + vector.size());
        }

        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }
        return result;
    }

    // proyeccion minima de la respuesta del proveedor
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<Item> data) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Item(List<Double> embedding) {
        }
    }
}
