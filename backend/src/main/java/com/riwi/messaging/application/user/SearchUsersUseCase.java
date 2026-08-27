package com.riwi.messaging.application.user;

import com.riwi.messaging.domain.model.UserPage;
import com.riwi.messaging.domain.port.UserAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: consulta de usuarios (SP a) con keyset pagination
@Service
public class SearchUsersUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UserAdminRepository users;

    public SearchUsersUseCase(UserAdminRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserPage execute(SearchUsersQuery query) {
        // acotamos el tamano de pagina antes de llamar al SP
        int limit = clampPageSize(query.size());
        // rw_search_users ya restringe que filas/campos ve un no-admin y si respeta includeInactive
        return users.search(query.query(), query.after(), limit, query.includeInactive());
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
