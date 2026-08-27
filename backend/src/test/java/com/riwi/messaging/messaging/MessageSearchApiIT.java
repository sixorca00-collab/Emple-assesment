package com.riwi.messaging.messaging;

import com.riwi.messaging.interfaces.rest.dto.ChannelResponse;
import com.riwi.messaging.interfaces.rest.dto.CreateChannelRequest;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.PostMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.SearchHitResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Consulta 2: cubre resaltado, alcance por membresia (RLS), keyset por relevancia y validacion de q
class MessageSearchApiIT extends AbstractRlsIT {

    private static final UUID ENGINEERING = UUID.fromString("22222222-2222-2222-2222-000000000002");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void matchedTermIsHighlightedInTheSnippet() {
        // Juan es owner del canal privado contractor-onboarding, donde aparece "contrato"
        String juan = token("juan.olarte@riwi.io");

        PageResponse<SearchHitResponse> page = search(juan, "contrato", null, null, null);

        assertThat(page.items()).isNotEmpty();
        SearchHitResponse hit = page.items().get(0);
        assertThat(hit.channelName()).isEqualTo("contractor-onboarding");
        assertThat(hit.snippet()).contains("<mark>").contains("</mark>");
        assertThat(hit.snippet().toLowerCase()).contains("<mark>contrato</mark>");
    }

    @Test
    void userGetsNoResultsFromChannelsHeIsNotMemberOf() {
        // "presupuesto" solo existe en product-planning (privado); Sebastian no es miembro
        String sebastian = token("sebastian.marin@riwi.io");

        PageResponse<SearchHitResponse> byPresupuesto = search(sebastian, "presupuesto", null, null, null);
        assertThat(byPresupuesto.items()).isEmpty();

        // "salarial" solo existe en hr-confidential (privado); Sebastian tampoco es miembro
        PageResponse<SearchHitResponse> bySalarial = search(sebastian, "salarial", null, null, null);
        assertThat(bySalarial.items()).isEmpty();
    }

    @Test
    void userGetsResultsFromChannelsHeBelongsTo() {
        // "keyset" aparece en engineering, canal del que Sebastian si es miembro
        String sebastian = token("sebastian.marin@riwi.io");

        PageResponse<SearchHitResponse> page = search(sebastian, "keyset", null, null, null);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(hit -> {
            assertThat(hit.channelId()).isEqualTo(ENGINEERING);
            assertThat(hit.channelName()).isEqualTo("engineering");
        });
    }

    @Test
    void channelIdFilterRestrictsResultsToThatChannel() {
        String andres = token("andres.gomez@riwi.io");
        UUID channelId = createChannel(andres, "it-search-scope-" + UUID.randomUUID());
        postMessage(andres, channelId, "termino exclusivo boysenberryqa aqui", null);

        // sin filtro aparece; filtrando por otro canal (engineering) no aparece
        assertThat(search(andres, "boysenberryqa", null, null, null).items()).isNotEmpty();
        assertThat(search(andres, "boysenberryqa", ENGINEERING, null, null).items()).isEmpty();
    }

    @Test
    void keysetPaginationDoesNotOverlapOrSkipAndExcludesSoftDeleted() {
        String andres = token("andres.gomez@riwi.io");
        UUID channelId = createChannel(andres, "it-search-keyset-" + UUID.randomUUID());

        List<UUID> posted = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            posted.add(postMessage(andres, channelId, "watermelonqa hit numero " + i, null).id());
        }
        // un mensaje mas con el termino que se borra: no debe aparecer en la busqueda
        UUID deletedId = postMessage(andres, channelId, "watermelonqa que se elimina", null).id();
        deleteMessage(andres, deletedId);

        // recorremos los resultados en paginas de 2 siguiendo el cursor
        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            PageResponse<SearchHitResponse> page = search(andres, "watermelonqa", null, cursor, 2);
            page.items().forEach(hit -> seen.add(hit.id()));
            cursor = page.nextCursor();
        } while (cursor != null);

        // sin duplicados, sin saltos y con el soft-deleted excluido
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(posted);
        assertThat(seen).doesNotContain(deletedId);
    }

    @Test
    void blankQueryYields400() {
        String andres = token("andres.gomez@riwi.io");

        ResponseEntity<ErrorResponse> blank = rest.exchange(
                "/messages/search?q={q}", HttpMethod.GET,
                new HttpEntity<>(authHeaders(andres)), ErrorResponse.class, "   ");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).isNotNull();
        assertThat(blank.getBody().code()).isEqualTo("INVALID_INPUT");

        ResponseEntity<ErrorResponse> missing = rest.exchange(
                "/messages/search", HttpMethod.GET,
                new HttpEntity<>(authHeaders(andres)), ErrorResponse.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        return response.getBody().id();
    }

    private MessageResponse postMessage(String token, UUID channelId, String body, UUID nonce) {
        ResponseEntity<MessageResponse> response = rest.exchange(
                "/channels/" + channelId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest(body, nonce), authHeaders(token)), MessageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void deleteMessage(String token, UUID messageId) {
        ResponseEntity<Void> response = rest.exchange(
                "/messages/" + messageId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private PageResponse<SearchHitResponse> search(String token, String q, UUID channelId, String cursor, Integer size) {
        StringBuilder url = new StringBuilder("/messages/search?q=").append(q);
        if (channelId != null) {
            url.append("&channelId=").append(channelId);
        }
        if (cursor != null) {
            url.append("&cursor=").append(cursor);
        }
        if (size != null) {
            url.append("&size=").append(size);
        }
        ResponseEntity<PageResponse<SearchHitResponse>> response = rest.exchange(
                url.toString(), HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
