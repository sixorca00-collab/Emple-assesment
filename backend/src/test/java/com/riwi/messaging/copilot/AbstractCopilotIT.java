package com.riwi.messaging.copilot;

import com.riwi.messaging.infrastructure.persistence.VectorLiterals;
import com.riwi.messaging.interfaces.rest.dto.ChannelResponse;
import com.riwi.messaging.interfaces.rest.dto.CreateChannelRequest;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PostMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.domain.port.EmbeddingPort;
import com.riwi.messaging.support.AbstractRlsIT;
import com.riwi.messaging.support.FakeAiConfig;
import com.riwi.messaging.support.RecordingChatPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.PreparedStatement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// base de los tests del copiloto: dobles de IA deterministas + helpers REST y de preparacion de embeddings
@Import(FakeAiConfig.class)
public abstract class AbstractCopilotIT extends AbstractRlsIT {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected EmbeddingPort embeddingPort;

    @Autowired
    protected RecordingChatPort chatPort;

    @DynamicPropertySource
    static void copilotProperties(DynamicPropertyRegistry registry) {
        // umbral alto: solo cuentan los embeddings que preparamos explicitamente en cada test
        registry.add("riwi.copilot.min-similarity", () -> "0.9");
        registry.add("riwi.copilot.top-k", () -> "6");
    }

    protected String token(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, "Password123!"), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected UUID createChannel(String token, String name, boolean isPrivate) {
        ResponseEntity<ChannelResponse> response = rest.exchange(
                "/channels", HttpMethod.POST,
                new HttpEntity<>(new CreateChannelRequest(name, "copilot test channel", isPrivate), authHeaders(token)),
                ChannelResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().id();
    }

    protected UUID postMessage(String token, UUID channelId, String body) {
        ResponseEntity<MessageResponse> response = rest.exchange(
                "/channels/" + channelId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest(body, null), authHeaders(token)), MessageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().id();
    }

    // fija el embedding de un mensaje al vector determinista del texto dado (bypassa RLS como bootstrap)
    protected void setMessageEmbedding(UUID messageId, String anchorText) {
        String literal = VectorLiterals.toLiteral(embeddingPort.embed(anchorText));
        runAsBootstrap(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE rw_message SET embedding = CAST(? AS vector) WHERE id = ?")) {
                ps.setString(1, literal);
                ps.setObject(2, messageId);
                ps.executeUpdate();
            }
        });
    }
}
