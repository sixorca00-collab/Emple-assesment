package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.SearchCursor;

import java.util.UUID;

// entrada del caso de uso de busqueda; el cursor ya viene decodificado desde la capa interfaces
public record SearchMessagesCommand(
        String q,
        UUID channelId,
        SearchCursor cursor,
        Integer size
) {
}
