package com.riwi.messaging.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// configuracion de la documentacion OpenAPI/Swagger de la API
@Configuration
public class OpenApiConfig {

    // nombre del esquema de seguridad referenciado por las operaciones autenticadas
    static final String BEARER_SCHEME = "bearer-jwt";

    // usamos SERVER_PORT (no server.port) para que el contrato publicado no herede el puerto aleatorio de los tests
    @Bean
    OpenAPI riwiOpenAPI(@Value("${SERVER_PORT:8080}") String serverPort) {
        // esquema Bearer JWT en la cabecera Authorization
        SecurityScheme bearerJwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                // metadatos del proyecto
                .info(new Info()
                        .title("Riwi Messaging API")
                        .version("0.1.0")
                        .description("API REST de la plataforma de mensajeria interna Riwi Co.: "
                                + "auth con JWT, canales, mensajes, busqueda full-text y copiloto RAG.")
                        .contact(new Contact().name("Riwi Co. Backend").email("dev@riwi.io"))
                        .license(new License().name("Proprietary - uso interno Riwi Co.")))
                // servidor local por defecto
                .servers(List.of(new Server().url("http://localhost:" + serverPort)))
                // registramos el esquema reutilizable
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerJwt))
                // por defecto toda operacion pide el Bearer; las publicas lo anulan con security = {}
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
