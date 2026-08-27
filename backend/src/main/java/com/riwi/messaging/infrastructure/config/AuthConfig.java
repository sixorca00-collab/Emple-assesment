package com.riwi.messaging.infrastructure.config;

import com.riwi.messaging.application.auth.RefreshTokenPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

// cablea la config de auth hacia la capa de aplicacion
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    // la aplicacion solo conoce la duracion del refresh, no la fuente de config
    @Bean
    RefreshTokenPolicy refreshTokenPolicy(JwtProperties properties) {
        return new RefreshTokenPolicy(Duration.ofDays(properties.refreshExpirationDays()));
    }

    // reloj UTC unico e inyectable para poder fijar el tiempo en pruebas
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
