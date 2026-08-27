package com.riwi.messaging.openapi;

import com.riwi.messaging.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// arranca el contexto real y publica el contrato OpenAPI versionado en docs/openapi.yaml
class OpenApiContractIT extends AbstractPostgresIT {

    // ruta relativa al modulo backend/: apunta a la carpeta docs/ de la raiz del repo
    private static final Path CONTRACT_FILE = Path.of("../docs/openapi.yaml");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void publishesOpenApiContractAsYaml() throws Exception {
        // pedimos el contrato en YAML al endpoint que expone springdoc
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs.yaml", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String yaml = response.getBody();
        assertThat(yaml).isNotBlank();
        // verificamos metadatos y esquema de seguridad esperados
        assertThat(yaml).contains("title: Riwi Messaging API");
        assertThat(yaml).contains("bearer-jwt");

        // escribimos el contrato en el repo para revisarlo en el PR
        Files.createDirectories(CONTRACT_FILE.getParent());
        Files.writeString(CONTRACT_FILE, yaml);
    }

    @Test
    void apiDocsJsonIsPubliclyReachable() {
        // el contrato en JSON responde sin Bearer
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"");
    }

    @Test
    void swaggerUiIsPubliclyReachable() {
        // Swagger UI carga sin autenticacion (sigue el redirect a index.html)
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
