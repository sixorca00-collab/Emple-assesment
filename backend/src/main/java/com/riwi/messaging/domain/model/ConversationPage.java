package com.riwi.messaging.domain.model;

import java.util.List;

// pagina de conversaciones con su cursor de continuacion (null si no hay mas)
public record ConversationPage(
        List<ConversationView> items,
        Cursor nextCursor
) {
}
