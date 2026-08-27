package com.riwi.messaging.interfaces.rest.dto;

import java.util.List;

// envoltura de pagina keyset: los items y el cursor opaco para pedir la siguiente (null si no hay mas)
public record PageResponse<T>(
        List<T> items,
        String nextCursor
) {
}
