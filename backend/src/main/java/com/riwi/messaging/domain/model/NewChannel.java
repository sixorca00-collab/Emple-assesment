package com.riwi.messaging.domain.model;

// datos de alta de un canal; el creador y su rol de owner los fija la BD
public record NewChannel(
        String name,
        String description,
        boolean isPrivate
) {
}
