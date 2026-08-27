package com.riwi.messaging.domain.model;

import java.util.UUID;

// cursor de keyset para la busqueda por relevancia: par (rank, id) de la ultima fila entregada
public record SearchCursor(
        double rank,
        UUID id
) {
}
