package com.riwi.messaging.application.auth;

// entrada del caso de uso de login (ya validada en la capa de interfaces)
public record LoginCommand(String email, String rawPassword) {
}
