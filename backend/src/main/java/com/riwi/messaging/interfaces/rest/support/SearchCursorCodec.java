package com.riwi.messaging.interfaces.rest.support;

import com.riwi.messaging.domain.model.SearchCursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

// codifica/decodifica el cursor de busqueda como token opaco base64 "<rank>|<uuid>"
public final class SearchCursorCodec {

    private SearchCursorCodec() {
    }

    // convierte el cursor de dominio (rank, id) en el token que viaja al cliente
    public static String encode(SearchCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = Double.toString(cursor.rank()) + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // reconstruye el cursor a partir del token recibido; null si no viene
    public static SearchCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException("malformed cursor");
            }
            double rank = Double.parseDouble(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new SearchCursor(rank, id);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("malformed cursor");
        }
    }
}
