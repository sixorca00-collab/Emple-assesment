package com.riwi.messaging.interfaces.rest.support;

import com.riwi.messaging.domain.model.Cursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

// codifica/decodifica el cursor de keyset como un token opaco base64 "<instant>|<uuid>"
public final class CursorCodec {

    private CursorCodec() {
    }

    // convierte el cursor de dominio en el token que viaja al cliente
    public static String encode(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.timestamp().toString() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // reconstruye el cursor de dominio a partir del token recibido; null si no viene
    public static Cursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException("malformed cursor");
            }
            Instant timestamp = Instant.parse(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new Cursor(timestamp, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException("malformed cursor");
        }
    }
}
