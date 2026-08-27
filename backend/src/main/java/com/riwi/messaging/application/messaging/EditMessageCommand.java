package com.riwi.messaging.application.messaging;

import java.util.UUID;

// entrada del caso de uso de edicion de mensaje
public record EditMessageCommand(
        UUID messageId,
        String body
) {
}
