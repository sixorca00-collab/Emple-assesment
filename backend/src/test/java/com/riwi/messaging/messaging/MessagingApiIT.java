package com.riwi.messaging.messaging;

import com.riwi.messaging.interfaces.rest.dto.AddMemberRequest;
import com.riwi.messaging.interfaces.rest.dto.ChannelResponse;
import com.riwi.messaging.interfaces.rest.dto.CreateChannelRequest;
import com.riwi.messaging.interfaces.rest.dto.EditMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.PostMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.support.AbstractRlsIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// cubre keyset, rechazo a no-miembro, soft delete, edicion por terceros y dedup por client_nonce
class MessagingApiIT extends AbstractRlsIT {

    private static final UUID ANDRES = UUID.fromString("11111111-1111-1111-1111-000000000003");
    private static final UUID VALENTINA = UUID.fromString("11111111-1111-1111-1111-000000000004");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void keysetPaginationDoesNotOverlapOrSkipAndExcludesSoftDeleted() {
        String andres = token("andres.gomez@riwi.io");
        UUID channelId = createChannel(andres, "it-keyset-" + UUID.randomUUID());

        // publicamos 5 mensajes vivos
        List<UUID> posted = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            posted.add(postMessage(andres, channelId, "keyset message " + i, null).id());
        }
        // y uno mas que borramos: no debe aparecer en el historial
        UUID deletedId = postMessage(andres, channelId, "to be deleted", null).id();
        deleteMessage(andres, deletedId);

        // recorremos el historial en paginas de 2 siguiendo el cursor
        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            PageResponse<MessageResponse> page = history(andres, channelId, cursor, 2);
            page.items().forEach(m -> seen.add(m.id()));
            cursor = page.nextCursor();
        } while (cursor != null);

        // orden descendente por creacion, sin duplicados y sin saltos
        List<UUID> expected = new ArrayList<>(posted);
        java.util.Collections.reverse(expected);
        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(seen).doesNotContain(deletedId);
    }

    @Test
    void nonMemberCannotReadHistoryNorPost() {
        String andres = token("andres.gomez@riwi.io");
        UUID channelId = createChannel(andres, "it-nonmember-" + UUID.randomUUID());
        postMessage(andres, channelId, "members only", null);

        // Laura no fue agregada al canal
        String laura = token("laura.cardona@riwi.io");

        ResponseEntity<ErrorResponse> read = rest.exchange(
                "/channels/" + channelId + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(laura)), ErrorResponse.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponse> post = rest.exchange(
                "/channels/" + channelId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest("intruder", null), authHeaders(laura)),
                ErrorResponse.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void softDeletedMessageDisappearsFromHistoryButRowAndReceiptsSurvive() {
        String andres = token("andres.gomez@riwi.io");
        String valentina = token("valentina.ruiz@riwi.io");
        UUID channelId = createChannel(andres, "it-softdelete-" + UUID.randomUUID());
        addMember(andres, channelId, VALENTINA);

        UUID messageId = postMessage(andres, channelId, "read me then delete me", null).id();

        // Valentina marca el canal como leido: se crea un acuse para el mensaje
        rest.exchange("/channels/" + channelId + "/read", HttpMethod.POST,
                new HttpEntity<>(authHeaders(valentina)), String.class);

        deleteMessage(andres, messageId);

        // ya no aparece en el historial
        PageResponse<MessageResponse> page = history(andres, channelId, null, 50);
        assertThat(page.items()).noneMatch(m -> m.id().equals(messageId));

        // la fila se conserva con sus campos de auditoria
        Map<String, Object> row = asActor(ANDRES, jdbc -> jdbc.queryForMap(
                "SELECT body, deleted_at, deleted_by FROM rw_message WHERE id = ?", messageId));
        assertThat(row.get("body")).isEqualTo("read me then delete me");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(ANDRES);

        // el acuse de lectura sigue existiendo
        Integer receipts = asActor(ANDRES, jdbc -> jdbc.queryForObject(
                "SELECT count(*) FROM rw_message_read_receipt WHERE message_id = ?", Integer.class, messageId));
        assertThat(receipts).isEqualTo(1);
    }

    @Test
    void onlyTheAuthorCanEditAMessage() {
        String andres = token("andres.gomez@riwi.io");
        String valentina = token("valentina.ruiz@riwi.io");
        UUID channelId = createChannel(andres, "it-edit-" + UUID.randomUUID());
        addMember(andres, channelId, VALENTINA);

        UUID messageId = postMessage(andres, channelId, "original body", null).id();

        // un tercero miembro no puede editar
        ResponseEntity<ErrorResponse> forbidden = rest.exchange(
                "/messages/" + messageId, HttpMethod.PATCH,
                new HttpEntity<>(new EditMessageRequest("hijacked"), authHeaders(valentina)),
                ErrorResponse.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // el autor si puede
        ResponseEntity<MessageResponse> edited = rest.exchange(
                "/messages/" + messageId, HttpMethod.PATCH,
                new HttpEntity<>(new EditMessageRequest("fixed body"), authHeaders(andres)),
                MessageResponse.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody()).isNotNull();
        assertThat(edited.getBody().body()).isEqualTo("fixed body");
        assertThat(edited.getBody().editedAt()).isNotNull();
    }

    @Test
    void duplicateClientNonceYieldsASingleMessage() {
        String andres = token("andres.gomez@riwi.io");
        UUID channelId = createChannel(andres, "it-nonce-" + UUID.randomUUID());
        UUID nonce = UUID.randomUUID();

        MessageResponse first = postMessage(andres, channelId, "sent once", nonce);
        MessageResponse retry = postMessage(andres, channelId, "sent once", nonce);

        // el reenvio devuelve el mismo mensaje, no crea otro
        assertThat(retry.id()).isEqualTo(first.id());
        PageResponse<MessageResponse> page = history(andres, channelId, null, 50);
        assertThat(page.items()).filteredOn(m -> m.body().equals("sent once")).hasSize(1);
    }

    // ---- helpers REST ----

    private String token(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, "Password123!"), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private UUID createChannel(String token, String name) {
        ResponseEntity<ChannelResponse> response = rest.exchange(
                "/channels", HttpMethod.POST,
                new HttpEntity<>(new CreateChannelRequest(name, "integration test channel", true), authHeaders(token)),
                ChannelResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().myRole()).isEqualTo("owner");
        return response.getBody().id();
    }

    private void addMember(String token, UUID channelId, UUID userId) {
        ResponseEntity<Void> response = rest.exchange(
                "/channels/" + channelId + "/members", HttpMethod.POST,
                new HttpEntity<>(new AddMemberRequest(userId, "member"), authHeaders(token)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private MessageResponse postMessage(String token, UUID channelId, String body, UUID nonce) {
        ResponseEntity<MessageResponse> response = rest.exchange(
                "/channels/" + channelId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest(body, nonce), authHeaders(token)), MessageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("sent");
        return response.getBody();
    }

    private void deleteMessage(String token, UUID messageId) {
        ResponseEntity<Void> response = rest.exchange(
                "/messages/" + messageId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private PageResponse<MessageResponse> history(String token, UUID channelId, String cursor, int size) {
        StringBuilder url = new StringBuilder("/channels/").append(channelId).append("/messages?size=").append(size);
        if (cursor != null) {
            url.append("&cursor=").append(cursor);
        }
        ResponseEntity<PageResponse<MessageResponse>> response = rest.exchange(
                url.toString(), HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
