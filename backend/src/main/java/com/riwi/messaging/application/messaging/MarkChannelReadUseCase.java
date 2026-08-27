package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: marca como leidos los mensajes ajenos vivos de un canal
@Service
public class MarkChannelReadUseCase {

    private final MessageRepository messages;

    public MarkChannelReadUseCase(MessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public int execute(UUID channelId) {
        // rw_mark_channel_read valida membresia y devuelve cuantos acuses inserto
        return messages.markChannelRead(channelId);
    }
}
