package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.ChannelView;
import com.riwi.messaging.domain.model.NewChannel;
import com.riwi.messaging.domain.port.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: crea un canal y deja al actor como owner
@Service
public class CreateChannelUseCase {

    private final ChannelRepository channels;

    public CreateChannelUseCase(ChannelRepository channels) {
        this.channels = channels;
    }

    @Transactional
    public ChannelView execute(CreateChannelCommand command) {
        // normalizamos la descripcion vacia a null para no guardar cadenas en blanco
        String description = command.description() == null || command.description().isBlank()
                ? null
                : command.description().trim();

        // la BD fija created_by y la membresia owner al actor de la transaccion (RLS)
        return channels.createWithOwner(new NewChannel(command.name().trim(), description, command.isPrivate()));
    }
}
