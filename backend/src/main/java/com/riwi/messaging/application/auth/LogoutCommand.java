package com.riwi.messaging.application.auth;

// entrada del caso de uso de logout: el refresh token a revocar
public record LogoutCommand(String rawRefreshToken) {
}
