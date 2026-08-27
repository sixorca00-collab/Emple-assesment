package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: soft delete de un mensaje via rw_soft_delete_message (autor o admin del canal)
@Service
public class DeleteMessageUseCase {

    private final MessageRepository messages;

    public DeleteMessageUseCase(MessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public void execute(UUID messageId) {
        // nunca hay borrado fisico: la funcion solo marca deleted_at/deleted_by
        messages.softDelete(messageId);
    }
}
