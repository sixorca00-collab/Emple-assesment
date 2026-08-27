package com.riwi.messaging.domain.model;

// evento de dominio: un mensaje nuevo que debe emitirse a los miembros conectados del canal
public record MessageBroadcast(
        MessageView message
) {
}
