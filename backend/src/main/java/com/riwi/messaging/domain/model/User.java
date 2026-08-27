package com.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

// identidad y credenciales del usuario (tabla rw_user)
public record User(
        UUID id,
        String email,
        String passwordHash,
        boolean platformAdmin,
        boolean active,
        Instant deletedAt
) {

    // un usuario soft-borrado no puede autenticarse
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // solo un usuario activo y no borrado puede iniciar sesion
    public boolean canAuthenticate() {
        return active && !isDeleted();
    }
}
