package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.SearchHit;

import java.time.Instant;
import java.util.UUID;

// resultado de busqueda para el cliente: incluye el snippet con el termino resaltado (<mark>...</mark>)
public record SearchHitResponse(
        UUID id,
        UUID channelId,
        String channelName,
        UUID senderId,
        String senderName,
        Instant createdAt,
        String snippet
) {

    public static SearchHitResponse from(SearchHit hit) {
        return new SearchHitResponse(
                hit.id(), hit.channelId(), hit.channelName(), hit.senderId(),
                hit.senderName(), hit.createdAt(), hit.snippet());
    }
}
