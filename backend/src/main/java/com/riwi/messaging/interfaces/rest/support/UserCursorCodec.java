package com.riwi.messaging.interfaces.rest.support;

import com.riwi.messaging.domain.model.UserSearchCursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

// codifica/decodifica el cursor de usuarios como token opaco base64 "<displayName>|<uuid>"
public final class UserCursorCodec {

    private UserCursorCodec() {
    }

    // convierte el cursor de dominio (displayName, id) en el token que viaja al cliente
    public static String encode(UserSearchCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.displayName() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // reconstruye el cursor a partir del token recibido; null si no viene
    public static UserSearchCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            // el display_name puede contener '|', el uuid es lo que sigue al ultimo separador
            int separator = raw.lastIndexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException("malformed cursor");
            }
            String displayName = raw.substring(0, separator);
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new UserSearchCursor(displayName, id);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("malformed cursor");
        }
    }
}
