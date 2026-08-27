package com.riwi.messaging.application.messaging;

import java.util.UUID;

// entrada del caso de uso de alta de miembro en un canal
public record AddMemberCommand(
        UUID channelId,
        UUID userId,
        String role
) {
}
