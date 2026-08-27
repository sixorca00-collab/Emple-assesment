package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.port.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: agrega un miembro a un canal (solo owner/admin del canal)
@Service
public class AddChannelMemberUseCase {

    private final ChannelRepository channels;

    public AddChannelMemberUseCase(ChannelRepository channels) {
        this.channels = channels;
    }

    @Transactional
    public void execute(AddMemberCommand command) {
        // desde la API solo se puede entrar como 'member' o 'admin'; 'owner' no se cede por esta via
        String requested = command.role() == null ? "" : command.role().trim().toLowerCase();
        String role = requested.equals("admin") ? "admin" : "member";
        // el adaptador valida en SQL que el actor sea owner/admin del canal
        channels.addMember(command.channelId(), command.userId(), role);
    }
}
