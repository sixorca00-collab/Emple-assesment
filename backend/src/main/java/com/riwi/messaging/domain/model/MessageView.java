package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// proyeccion de lectura de un mensaje, con el nombre del emisor ya resuelto
public record MessageView(
        UUID id,
        UUID channelId,
        UUID senderId,
        String senderName,
        String body,
        String status,
        Instant createdAt,
        Instant editedAt
) {
}
