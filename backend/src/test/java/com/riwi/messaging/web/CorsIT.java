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

// CORS abierto para el demo: cualquier origen recibe Access-Control-Allow-Origin (Spring lo refleja)
class CorsIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void preflightOnLoginIsAllowedWithoutAuth() {
        String origin = "http://localhost:14200";
        ResponseEntity<Void> response = preflight("/auth/login", origin);

        // el preflight OPTIONS no requiere autenticacion
        assertThat(response.getStatusCode().value()).isIn(200, 204);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
    }

    @Test
    void actualLoginResponseCarriesTheCorsHeader() {
        String origin = "http://localhost:14200";
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);

        ResponseEntity<Void> response = rest.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("nobody@riwi.io", "whatever"), headers), Void.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
    }

    @Test
    void preflightOnRegisterIsAllowed() {
        String origin = "http://localhost:14200";
        ResponseEntity<Void> response = preflight("/auth/register", origin);

        assertThat(response.getStatusCode().value()).isIn(200, 204);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
    }

    @Test
    void anyOriginIsAllowedForTheDemo() {
        // un host cualquiera (LAN, otro puerto) tambien pasa
        String origin = "http://192.168.1.50:9999";
        ResponseEntity<Void> response = preflight("/auth/login", origin);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
    }

    private ResponseEntity<Void> preflight(String path, String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.set("Access-Control-Request-Method", "POST");
        return rest.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);
    }
}
