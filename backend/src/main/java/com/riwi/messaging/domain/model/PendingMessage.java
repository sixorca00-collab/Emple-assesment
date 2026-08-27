package com.riwi.messaging.domain.model;

import java.util.UUID;

// mensaje vivo sin embedding a la espera de backfill
public record PendingMessage(
        UUID messageId,
        String body
) {
}
