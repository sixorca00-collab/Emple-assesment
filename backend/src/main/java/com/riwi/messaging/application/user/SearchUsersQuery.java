package com.riwi.messaging.application.user;

import com.riwi.messaging.domain.model.UserSearchCursor;

// entrada del caso de uso de consulta de usuarios
public record SearchUsersQuery(
        String query,
        UserSearchCursor after,
        Integer size,
        boolean includeInactive
) {
}
