package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.ChannelView;

import java.time.Instant;
import java.util.UUID;

// respuesta de un canal creado o consultado
public record ChannelResponse(
        UUID id,
        String name,
        String description,
        boolean isPrivate,
        String myRole,
        Instant createdAt
) {

    public static ChannelResponse from(ChannelView view) {
        return new ChannelResponse(
                view.id(), view.name(), view.description(),
                view.isPrivate(), view.myRole(), view.createdAt());
    }
}
