package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: edita un mensaje via rw_edit_message (solo el autor)
@Service
public class EditMessageUseCase {

    private final MessageRepository messages;

    public EditMessageUseCase(MessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public MessageView execute(EditMessageCommand command) {
        // rw_edit_message rechaza a quien no sea el autor (SQLSTATE 42501)
        return messages.edit(command.messageId(), command.body());
    }
}
