package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.MessageView;

import java.time.Instant;
import java.util.UUID;

// respuesta de un mensaje individual
public record MessageResponse(
        UUID id,
        UUID channelId,
        UUID senderId,
        String senderName,
        String body,
        String status,
        Instant createdAt,
        Instant editedAt
) {

    public static MessageResponse from(MessageView view) {
        return new MessageResponse(
                view.id(), view.channelId(), view.senderId(), view.senderName(),
                view.body(), view.status(), view.createdAt(), view.editedAt());
    }
}
