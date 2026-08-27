package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// una conversacion del actor tal como la expone la vista rw_user_conversation
public record ConversationView(
        UUID channelId,
        String channelName,
        boolean isPrivate,
        String myRole,
        UUID lastMessageId,
        String lastMessagePreview,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        long unreadCount,
        // clave de orden usada para el keyset (last_message_at o epoch si el canal no tiene mensajes)
        Instant sortKey
) {
}
