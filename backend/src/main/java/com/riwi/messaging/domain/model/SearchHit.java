package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// resultado de busqueda: mensaje coincidente con el fragmento resaltado (<mark>...</mark>) ya armado
public record SearchHit(
        UUID id,
        UUID channelId,
        String channelName,
        UUID senderId,
        String senderName,
        Instant createdAt,
        String snippet,
        double rank
) {
}
