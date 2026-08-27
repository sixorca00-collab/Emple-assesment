package com.riwi.messaging.auth;

import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.MeResponse;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// valida el circuito completo: JWT -> SecurityContext -> aspecto que fija el actor RLS -> lectura en BD
class MeEndpointIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void meReturnsProfileAndActorScopedConversationsForAuthenticatedUser() {
        // Camila Restrepo es Product Manager y miembro de varios canales
        String accessToken = login("camila.restrepo@riwi.io");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<MeResponse> response = rest.exchange(
                "/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("camila.restrepo@riwi.io");
        assertThat(response.getBody().displayName()).isEqualTo("Camila Restrepo");
        assertThat(response.getBody().jobTitle()).isEqualTo("Product Manager");
        assertThat(response.getBody().platformAdmin()).isFalse();
        // la vista rw_user_conversation solo devuelve filas si el aspecto fijo app.current_user_id
        assertThat(response.getBody().visibleConversationCount()).isGreaterThan(0);
    }

    @Test
    void meRequiresAuthentication() {
        ResponseEntity<ErrorResponse> response = rest.getForEntity("/me", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void meRejectsAGarbageToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-jwt");
        ResponseEntity<ErrorResponse> response = rest.exchange(
                "/me", HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthIsPublic() {
        assertThat(rest.getForEntity("/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void everyResponseCarriesACorrelationIdHeader() {
        ResponseEntity<String> response = rest.getForEntity("/health", String.class);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    private String login(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, "Password123!"), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }
}
