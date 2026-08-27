package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.UserPage;
import com.riwi.messaging.domain.model.UserSearchCursor;
import com.riwi.messaging.domain.model.UserSummary;

import java.util.Optional;
import java.util.UUID;

// puerto de gestion de usuarios; se apoya en los stored procedures rw_search_users / rw_update_user / rw_delete_user
public interface UserAdminRepository {

    // consulta de usuarios con keyset por (lower(display_name), id); el SP restringe filas y campos del no-admin
    UserPage search(String query, UserSearchCursor after, int limit, boolean includeInactive);

    // edicion via rw_update_user; el SP valida permisos y deja intactos los parametros nulos
    void update(UUID targetId, String displayName, String jobTitle, String avatarUrl, String bio, Boolean active);

    // soft delete via rw_delete_user; el SP marca deleted_at, desactiva y revoca refresh tokens
    void delete(UUID targetId);

    // relee un usuario vigente por id para responder el cuerpo del PATCH
    Optional<UserSummary> findById(UUID userId);
}
