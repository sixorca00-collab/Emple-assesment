package com.riwi.messaging.application.auth;

// entrada del caso de uso de registro (ya validada en la capa de interfaces)
public record RegisterCommand(String name, String jobTitle, String email, String rawPassword) {
}
