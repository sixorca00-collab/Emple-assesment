package com.riwi.messaging.application.user;

import com.riwi.messaging.domain.port.UserAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: eliminacion de usuario (SP b.2); siempre soft delete, nunca borrado fisico
@Service
public class DeleteUserUseCase {

    private final UserAdminRepository users;

    public DeleteUserUseCase(UserAdminRepository users) {
        this.users = users;
    }

    @Transactional
    public void execute(UUID targetId) {
        // rw_delete_user marca deleted_at, desactiva y revoca los refresh tokens del usuario
        users.delete(targetId);
    }
}
