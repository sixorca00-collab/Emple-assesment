package com.riwi.messaging.auth;

import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.MeResponse;
import com.riwi.messaging.interfaces.rest.dto.RegisterRequest;
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

// tests del registro con auto-login: 201 + par de tokens, validaciones y choque de correo
class RegisterIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerCreatesAccountAndReturnsUsableTokens() {
        RegisterRequest body = new RegisterRequest(
                "Nora Vega", "Support Lead", "nora.vega@riwi.io", "Str0ngPass!");

        ResponseEntity<TokenResponse> response = rest.postForEntity("/auth/register", body, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");

        // el access token recien emitido sirve para el circuito completo hasta la BD
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().accessToken());
        ResponseEntity<MeResponse> me = rest.exchange(
                "/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().email()).isEqualTo("nora.vega@riwi.io");
        assertThat(me.getBody().displayName()).isEqualTo("Nora Vega");
        assertThat(me.getBody().jobTitle()).isEqualTo("Support Lead");
        // el usuario nace sin privilegios de plataforma
        assertThat(me.getBody().platformAdmin()).isFalse();
    }

    @Test
    void registerPersistsABcryptHashNeverThePlainPassword() {
        RegisterRequest body = new RegisterRequest(
                "Hugo Sanz", "Analyst", "hugo.sanz@riwi.io", "Str0ngPass!");

        ResponseEntity<TokenResponse> response = rest.postForEntity("/auth/register", body, TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // leemos el hash directamente de la tabla como superusuario bootstrap
        String[] storedHash = new String[1];
        runAsBootstrap(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT password_hash FROM rw_user WHERE lower(email) = lower(?)")) {
                ps.setString(1, "hugo.sanz@riwi.io");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    storedHash[0] = rs.getString(1);
                }
            }
        });

        assertThat(storedHash[0]).startsWith("$2");
        assertThat(storedHash[0]).isNotEqualTo("Str0ngPass!");
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        RegisterRequest body = new RegisterRequest(
                "Fake Juan", "Impersonator", "juan.olarte@riwi.io", "Str0ngPass!");

        ResponseEntity<ErrorResponse> response = rest.postForEntity("/auth/register", body, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_EMAIL_TAKEN");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void registerRejectsAShortPassword() {
        RegisterRequest body = new RegisterRequest(
                "Ana Diaz", "Recruiter", "ana.diaz@riwi.io", "short7c");

        ResponseEntity<ErrorResponse> response = rest.postForEntity("/auth/register", body, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsAnInvalidEmail() {
        RegisterRequest body = new RegisterRequest(
                "Leo Prado", "Designer", "not-an-email", "Str0ngPass!");

        ResponseEntity<ErrorResponse> response = rest.postForEntity("/auth/register", body, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsAOneCharacterName() {
        RegisterRequest body = new RegisterRequest(
                "L", "Designer", "leo.prado@riwi.io", "Str0ngPass!");

        ResponseEntity<ErrorResponse> response = rest.postForEntity("/auth/register", body, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
