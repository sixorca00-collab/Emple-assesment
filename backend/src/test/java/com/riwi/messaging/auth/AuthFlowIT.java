package com.riwi.messaging.auth;

import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.LogoutRequest;
import com.riwi.messaging.interfaces.rest.dto.RefreshRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// tests del flujo de autenticacion: login, rotacion de refresh y deteccion de reuso
class AuthFlowIT extends AbstractPostgresIT {

    private static final String EMAIL = "andres.gomez@riwi.io";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginSucceedsWithValidCredentials() {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login", new LoginRequest(EMAIL, PASSWORD), TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginFailsWithWrongPassword() {
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/auth/login", new LoginRequest(EMAIL, "wrong-password"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void loginFailsWithUnknownEmailUsingTheSameGenericError() {
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/auth/login", new LoginRequest("nobody@riwi.io", PASSWORD), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void refreshRotatesTheTokenAndInvalidatesTheOldOne() {
        TokenResponse login = login();

        ResponseEntity<TokenResponse> rotated = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(login.refreshToken()), TokenResponse.class);

        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rotated.getBody()).isNotNull();
        assertThat(rotated.getBody().refreshToken()).isNotEqualTo(login.refreshToken());

        // el refresh anterior ya no sirve
        ResponseEntity<ErrorResponse> reused = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(login.refreshToken()), ErrorResponse.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshReuseRevokesTheWholeChain() {
        TokenResponse login = login();

        // rotacion legitima
        TokenResponse rotated = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(login.refreshToken()), TokenResponse.class).getBody();
        assertThat(rotated).isNotNull();

        // reuso del token ya rotado: dispara la defensa y revoca toda la cadena del usuario
        ResponseEntity<ErrorResponse> reuse = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(login.refreshToken()), ErrorResponse.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody()).isNotNull();
        assertThat(reuse.getBody().code()).isEqualTo("AUTH_TOKEN_REUSE");

        // incluso el refresh nuevo (legitimo) quedo revocado por la deteccion de reuso
        ResponseEntity<ErrorResponse> afterBreach = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(rotated.refreshToken()), ErrorResponse.class);
        assertThat(afterBreach.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterBreach.getBody()).isNotNull();
        assertThat(afterBreach.getBody().code()).isEqualTo("AUTH_TOKEN_REUSE");
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TokenResponse login = login();

        ResponseEntity<Void> logout = rest.postForEntity(
                "/auth/logout", new LogoutRequest(login.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> afterLogout = rest.postForEntity(
                "/auth/refresh", new RefreshRequest(login.refreshToken()), ErrorResponse.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private TokenResponse login() {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(EMAIL, PASSWORD), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body;
    }
}
