package com.riwi.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// config del JWT tomada de variables de entorno (nunca hardcodeada)
@ConfigurationProperties(prefix = "riwi.jwt")
public record JwtProperties(
        String secret,
        int accessExpirationMinutes,
        int refreshExpirationDays
) {
}
