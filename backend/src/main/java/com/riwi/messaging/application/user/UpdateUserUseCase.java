package com.riwi.messaging.application.user;

import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import com.riwi.messaging.domain.model.UserSummary;
import com.riwi.messaging.domain.port.UserAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: edicion de usuario (SP b.1); el SP decide si el actor puede y que campos toca
@Service
public class UpdateUserUseCase {

    private final UserAdminRepository users;

    public UpdateUserUseCase(UserAdminRepository users) {
        this.users = users;
    }

    @Transactional
    public UserSummary execute(UpdateUserCommand command) {
        // rw_update_user lanza 42501 si no autorizado y P0002 si el usuario no existe
        users.update(
                command.targetId(),
                command.displayName(),
                command.jobTitle(),
                command.avatarUrl(),
                command.bio(),
                command.active());

        // releemos el usuario ya actualizado en la misma transaccion para responder el cuerpo
        return users.findById(command.targetId())
                .orElseThrow(() -> new ResourceNotFoundException("user not found after update"));
    }
}
