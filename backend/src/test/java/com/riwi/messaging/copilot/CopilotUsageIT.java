package com.riwi.messaging.copilot;

import com.riwi.messaging.interfaces.rest.dto.CopilotHistoryResponse;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryRequest;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryResponse;
import com.riwi.messaging.interfaces.rest.dto.CopilotUsageResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Consulta 4: consumo acumulado del copiloto; aislamiento por actor y desglose para admin; historial keyset
class CopilotUsageIT extends AbstractCopilotIT {

    private static final UUID ANDRES = UUID.fromString("11111111-1111-1111-1111-000000000003");

    @Test
    void usageSumsPersistedTokensAndDoesNotLeakOtherUsers() {
        String andres = token("andres.gomez@riwi.io");
        UUID channel = createChannel(andres, "cop-usage-" + UUID.randomUUID(), true);

        // dos consultas respondidas nuevas para Andres
        for (int i = 0; i < 2; i++) {
            UUID message = postMessage(andres, channel, "Dato de contexto numero " + i + " para el reporte de uso.");
            String question = "pregunta de uso " + i + " " + UUID.randomUUID();
            setMessageEmbedding(message, question);
            ask(andres, question);
        }

        // total esperado calculado desde lo persistido en la BD
        Long expectedTotal = asActor(ANDRES, jdbc -> jdbc.queryForObject(
                "SELECT coalesce(sum(total_tokens), 0) FROM rw_copilot_query WHERE user_id = ?", Long.class, ANDRES));

        List<CopilotUsageResponse> rows = usage(andres, null);

        // un actor no admin solo se ve a si mismo
        assertThat(rows).hasSize(1);
        CopilotUsageResponse mine = rows.get(0);
        assertThat(mine.userId()).isEqualTo(ANDRES);
        assertThat(mine.totalTokens()).isEqualTo(expectedTotal);
        assertThat(mine.answeredCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void platformAdminSeesBreakdownPerUser() {
        String juan = token("juan.olarte@riwi.io");

        List<CopilotUsageResponse> all = usage(juan, null);
        // el seed ya trae consultas de varios usuarios
        assertThat(all.size()).isGreaterThanOrEqualTo(2);

        List<CopilotUsageResponse> filtered = usage(juan, ANDRES);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).userId()).isEqualTo(ANDRES);
    }

    @Test
    void historyReturnsOnlyOwnQueriesWithKeysetPagination() {
        String andres = token("andres.gomez@riwi.io");

        Long owned = asActor(ANDRES, jdbc -> jdbc.queryForObject(
                "SELECT count(*) FROM rw_copilot_query WHERE user_id = ?", Long.class, ANDRES));

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            PageResponse<CopilotHistoryResponse> page = history(andres, cursor);
            page.items().forEach(item -> seen.add(item.id()));
            cursor = page.nextCursor();
        } while (cursor != null);

        // el historial devuelve exactamente las consultas del actor, sin duplicados
        assertThat(seen).doesNotHaveDuplicates();
        assertThat((long) seen.size()).isEqualTo(owned);

        List<UUID> others = asActor(ANDRES, jdbc -> jdbc.queryForList(
                "SELECT id FROM rw_copilot_query WHERE user_id <> ?", UUID.class, ANDRES));
        assertThat(seen).doesNotContainAnyElementsOf(others);
    }

    private CopilotQueryResponse ask(String token, String question) {
        ResponseEntity<CopilotQueryResponse> response = rest.exchange(
                "/copilot/query", HttpMethod.POST,
                new HttpEntity<>(new CopilotQueryRequest(question), authHeaders(token)),
                CopilotQueryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<CopilotUsageResponse> usage(String token, UUID userId) {
        String url = "/copilot/usage" + (userId == null ? "" : "?userId=" + userId);
        ResponseEntity<List<CopilotUsageResponse>> response = rest.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private PageResponse<CopilotHistoryResponse> history(String token, String cursor) {
        String url = "/copilot/history?size=2" + (cursor == null ? "" : "&cursor=" + cursor);
        ResponseEntity<PageResponse<CopilotHistoryResponse>> response = rest.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
