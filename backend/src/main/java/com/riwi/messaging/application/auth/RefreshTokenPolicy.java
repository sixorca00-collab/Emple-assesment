package com.riwi.messaging.application.auth;

import java.time.Duration;

// politica de vida del refresh token; la infraestructura la construye desde la config
public record RefreshTokenPolicy(Duration timeToLive) {
}
