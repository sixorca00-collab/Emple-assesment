package com.riwi.messaging.domain.model;

import java.util.List;

// pagina keyset del historial del copiloto; cursor null si no hay mas
public record CopilotHistoryPage(
        List<CopilotHistoryEntry> items,
        Cursor nextCursor
) {
}
