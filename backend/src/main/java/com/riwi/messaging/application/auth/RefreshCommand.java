package com.riwi.messaging.application.auth;

// entrada del caso de uso de refresh: el valor opaco del refresh token actual
public record RefreshCommand(String rawRefreshToken) {
}
