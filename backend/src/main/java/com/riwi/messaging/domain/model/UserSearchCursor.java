package com.riwi.messaging.domain.model;

import java.util.UUID;

// cursor de keyset de la busqueda de usuarios: par (displayName, id) de la ultima fila entregada
public record UserSearchCursor(
        String displayName,
        UUID id
) {
}
