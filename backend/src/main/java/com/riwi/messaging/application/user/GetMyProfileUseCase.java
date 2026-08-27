package com.riwi.messaging.application.user;

import com.riwi.messaging.domain.exception.ResourceNotFoundException;
import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.port.ConversationRepository;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: devuelve el perfil del actor autenticado leido de la BD
@Service
public class GetMyProfileUseCase {

    private final UserRepository users;
    private final ConversationRepository conversations;

    public GetMyProfileUseCase(UserRepository users, ConversationRepository conversations) {
        this.users = users;
        this.conversations = conversations;
    }

    @Transactional(readOnly = true)
    public MyProfileView execute(UUID actorId) {
        // el aspecto de transaccion ya fijo app.current_user_id para este actor
        ActorProfile profile = users.findProfileById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        // la vista rw_user_conversation solo devuelve las conversaciones del actor (RLS + security_invoker)
        long visible = conversations.countForCurrentActor();

        return new MyProfileView(
                profile.userId(),
                profile.email(),
                profile.displayName(),
                profile.jobTitle(),
                profile.platformAdmin(),
                visible);
    }
}
