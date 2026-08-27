package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// cursor de keyset: par (timestamp, id) que identifica la ultima fila ya entregada
public record Cursor(
        Instant timestamp,
        UUID id
) {
}
