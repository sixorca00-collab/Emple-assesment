package com.riwi.messaging.application.messaging;

import java.util.UUID;

// entrada del caso de uso de publicacion; clientNonce es opcional (dedup de reenvios)
public record PostMessageCommand(
        UUID channelId,
        String body,
        UUID clientNonce
) {
}
