package com.riwi.messaging.infrastructure.websocket;

import com.riwi.messaging.domain.model.MessageView;

// forma del evento JSON que reciben los clientes WebSocket
record MessageEventPayload(
        String type,
        MessageView message
) {

    static MessageEventPayload created(MessageView message) {
        return new MessageEventPayload("message.created", message);
    }
}
