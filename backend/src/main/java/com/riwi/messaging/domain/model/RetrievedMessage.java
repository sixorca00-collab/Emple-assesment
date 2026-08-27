package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// fragmento de contexto recuperado por el RAG (Consulta 3): mensaje + metadatos + similitud
public record RetrievedMessage(
        UUID messageId,
        UUID channelId,
        String channelName,
        UUID senderId,
        String authorName,
        String authorJobTitle,
        String body,
        Instant createdAt,
        double similarity
) {
}
