package com.riwi.messaging.copilot;

import com.riwi.messaging.interfaces.rest.dto.BackfillResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// backfill de embeddings: solo administradores de plataforma; puebla los vectores faltantes
class EmbeddingBackfillIT extends AbstractCopilotIT {

    @Test
    void backfillIsAdminOnlyAndPopulatesMissingEmbeddings() {
        String andres = token("andres.gomez@riwi.io");
        String juan = token("juan.olarte@riwi.io");

        UUID channel = createChannel(andres, "cop-backfill-" + UUID.randomUUID(), true);
        UUID message = postMessage(andres, channel, "Mensaje recien publicado sin embedding todavia.");

        // un usuario no admin no puede disparar el backfill
        ResponseEntity<String> forbidden = rest.exchange(
                "/internal/embeddings/backfill", HttpMethod.POST,
                new HttpEntity<>(authHeaders(andres)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // el admin si: procesa al menos el mensaje recien creado
        ResponseEntity<BackfillResponse> ok = rest.exchange(
                "/internal/embeddings/backfill", HttpMethod.POST,
                new HttpEntity<>(authHeaders(juan)), BackfillResponse.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isNotNull();
        assertThat(ok.getBody().skipped()).isFalse();
        assertThat(ok.getBody().processed()).isGreaterThanOrEqualTo(1);

        Boolean hasEmbedding = asActor(
                UUID.fromString("11111111-1111-1111-1111-000000000003"),
                jdbc -> jdbc.queryForObject(
                        "SELECT embedding IS NOT NULL FROM rw_message WHERE id = ?", Boolean.class, message));
        assertThat(hasEmbedding).isTrue();
    }
}
