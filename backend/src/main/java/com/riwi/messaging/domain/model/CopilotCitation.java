package com.riwi.messaging.domain.model;

import java.util.UUID;

// cita a un mensaje fuente usado por el copiloto; rank = orden de relevancia (1 = mas relevante)
public record CopilotCitation(
        UUID messageId,
        UUID channelId,
        String snippet,
        int rank
) {
}
