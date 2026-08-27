package com.riwi.messaging.domain.model;

import java.util.List;

// pagina de historial: los items y el cursor para pedir la siguiente (null si no hay mas)
public record MessagePage(
        List<MessageView> items,
        Cursor nextCursor
) {
}
