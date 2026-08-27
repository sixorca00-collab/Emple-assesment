package com.riwi.messaging.domain.model;

import java.util.List;

// pagina de usuarios: los items y el cursor para pedir la siguiente (null si no hay mas)
public record UserPage(
        List<UserSummary> items,
        UserSearchCursor nextCursor
) {
}
