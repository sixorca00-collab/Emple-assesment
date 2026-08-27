package com.riwi.messaging.ai;

import com.riwi.messaging.infrastructure.ai.OpenAiEmbeddingAdapter;
import com.riwi.messaging.infrastructure.config.AiEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// verifica que el adaptador de embeddings manda "dimensions" y parsea una respuesta de la dimension configurada
class OpenAiEmbeddingAdapterTest {

    private static final int DIMENSIONS = 1536;

    @Test
    void sendsDimensionsAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder();
        // servidor HTTP falso enlazado al builder del adaptador
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String vector = "0.5,".repeat(DIMENSIONS - 1) + "0.5";
        String responseBody = "{\"data\":[{\"embedding\":[" + vector + "]}]}";

        // el request debe incluir dimensions ademas de model e input
        server.expect(requestTo("https://fake.ai/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input").value("hola mundo"))
                .andExpect(jsonPath("$.dimensions").value(DIMENSIONS))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        var properties = new AiEmbeddingProperties("https://fake.ai", "sk-test", "text-embedding-3-small", DIMENSIONS);
        var adapter = new OpenAiEmbeddingAdapter(properties, builder);

        float[] result = adapter.embed("hola mundo");

        assertThat(result).hasSize(DIMENSIONS);
        assertThat(result[0]).isEqualTo(0.5f);
        server.verify();
    }

    @Test
    void rejectsResponseWithWrongDimension() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // respuesta de 3 dimensiones cuando se esperan 1536
        server.expect(requestTo("https://fake.ai/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}", MediaType.APPLICATION_JSON));

        var properties = new AiEmbeddingProperties("https://fake.ai", "sk-test", "text-embedding-3-small", DIMENSIONS);
        var adapter = new OpenAiEmbeddingAdapter(properties, builder);

        assertThatThrownBy(() -> adapter.embed("hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension mismatch");
    }
}
