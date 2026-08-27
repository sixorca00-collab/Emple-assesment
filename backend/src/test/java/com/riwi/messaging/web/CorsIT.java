package com.riwi.messaging.web;

import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// verifica que el backend responde CORS a los origenes del frontend y rechaza los demas
class CorsIT extends AbstractPostgresIT {

    private static final String ALLOWED_ORIGIN = "http://localhost:14200";
    private static final String EVIL_ORIGIN = "http://evil.com";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void preflightOnLoginReflectsTheAllowedOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);
        headers.set("Access-Control-Request-Method", "POST");

        // preflight OPTIONS: no debe requerir autenticacion
        ResponseEntity<Void> response = rest.exchange(
                "/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().value()).isIn(200, 204);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void actualLoginResponseCarriesTheCorsHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);

        ResponseEntity<Void> response = rest.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("nobody@riwi.io", "whatever"), headers), Void.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void disallowedOriginGetsNoCorsHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(EVIL_ORIGIN);
        headers.set("Access-Control-Request-Method", "POST");

        ResponseEntity<Void> response = rest.exchange(
                "/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
}
