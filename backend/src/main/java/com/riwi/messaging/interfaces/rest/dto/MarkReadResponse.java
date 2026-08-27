package com.riwi.messaging.interfaces.rest.dto;

// resultado de marcar un canal como leido: cuantos acuses se registraron
public record MarkReadResponse(
        int markedRead
) {
}
