package com.riwi.messaging.copilot;

import com.riwi.messaging.application.copilot.BackfillEmbeddingsUseCase;
import com.riwi.messaging.application.copilot.BackfillMode;
import com.riwi.messaging.domain.port.ChatPort;
import com.riwi.messaging.domain.port.EmbeddingPort;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryRequest;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.support.AbstractRlsIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// smoke opt-in contra los proveedores reales (Groq + embeddings). NO corre en `mvn test` sin API keys.
// ejecutar con: mvn -f backend/pom.xml test -Dtest=CopilotLiveIT  (con AI_CHAT_API_KEY / AI_EMBEDDING_API_KEY cargadas)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AI_EMBEDDING_API_KEY", matches = ".+")
class CopilotLiveIT extends AbstractRlsIT {

    @Autowired
    private EmbeddingPort embeddingPort;

    @Autowired
    private ChatPort chatPort;

    @Autowired
    private BackfillEmbeddingsUseCase backfill;

    @Autowired
    private TestRestTemplate rest;

    // dimension esperada: la del entorno o 1536 por defecto
    private static int expectedDimensions() {
        String raw = System.getenv("AI_EMBEDDING_DIMENSIONS");
        return raw == null || raw.isBlank() ? 1536 : Integer.parseInt(raw.trim());
    }

    @Test
    void realEmbeddingHasConfiguredDimension() {
        // embedding real de una frase: la dimension debe casar con AI_EMBEDDING_DIMENSIONS
        float[] vector = embeddingPort.embed("El presupuesto de infraestructura del trimestre");
        assertThat(vector).hasSize(expectedDimensions());
    }

    @Test
    void realChatAnswersWithTokenUsage() {
        // chat real: respuesta no vacia y consumo de tokens reportado
        ChatPort.ChatResult result = chatPort.complete(
                "Eres un asistente breve. Responde con una sola frase.",
                "Saluda en espanol.");
        assertThat(result.content()).isNotBlank();
        assertThat(result.promptTokens() + result.completionTokens()).isPositive();
    }

    @Test
    void endToEndAnswersFromSeedAndRefusesOutOfContext() {
        // re-embebe TODO el corpus del seed con el proveedor real (sobrescribe los vectores sinteticos)
        backfill.execute(BackfillMode.ALL);

        // andres.gomez es miembro de product-planning: la pregunta matchea un mensaje del corpus
        String andres = login("andres.gomez@riwi.io");
        CopilotQueryResponse answered = ask(andres, "cual es el presupuesto de Q3 para infraestructura");
        assertThat(answered.status()).isEqualTo("answered");
        assertThat(answered.citations()).isNotEmpty();

        // pregunta sin relacion con ningun canal => negativa honesta por falta de contexto
        CopilotQueryResponse refused = ask(andres, "cual es la receta tradicional del ajiaco santafereno");
        assertThat(refused.status()).isEqualTo("refused_no_context");
    }

    private String login(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, "Password123!"), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }

    private CopilotQueryResponse ask(String token, String question) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<CopilotQueryResponse> response = rest.exchange(
                "/copilot/query", HttpMethod.POST,
                new HttpEntity<>(new CopilotQueryRequest(question), headers), CopilotQueryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
