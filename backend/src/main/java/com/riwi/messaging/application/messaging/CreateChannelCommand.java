package com.riwi.messaging.application.messaging;

// entrada del caso de uso de creacion de canal
public record CreateChannelCommand(
        String name,
        String description,
        boolean isPrivate
) {
}
