package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.MessageBroadcast;
import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.domain.port.MessageBroadcastPort;
import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// caso de uso: publica un mensaje via rw_post_message y lo emite en tiempo real
@Service
public class PostMessageUseCase {

    private final MessageRepository messages;
    private final MessageBroadcastPort broadcaster;

    public PostMessageUseCase(MessageRepository messages, MessageBroadcastPort broadcaster) {
        this.messages = messages;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public MessageView execute(PostMessageCommand command) {
        // rw_post_message valida membresia en la BD y deduplica por clientNonce
        MessageView persisted = messages.post(command.channelId(), command.body(), command.clientNonce());

        // emitimos el evento SOLO tras confirmar la transaccion, para que el receptor ya pueda leerlo
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcaster.broadcast(new MessageBroadcast(persisted));
            }
        });

        return persisted;
    }
}
