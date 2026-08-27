package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.ConversationView;

import java.time.Instant;
import java.util.UUID;

// respuesta de una conversacion del actor
public record ConversationResponse(
        UUID channelId,
        String channelName,
        boolean isPrivate,
        String myRole,
        UUID lastMessageId,
        String lastMessagePreview,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        long unreadCount
) {

    public static ConversationResponse from(ConversationView view) {
        return new ConversationResponse(
                view.channelId(), view.channelName(), view.isPrivate(), view.myRole(),
                view.lastMessageId(), view.lastMessagePreview(), view.lastMessageSenderId(),
                view.lastMessageAt(), view.unreadCount());
    }
}
