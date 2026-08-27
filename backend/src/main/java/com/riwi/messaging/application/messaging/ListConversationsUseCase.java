package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.ConversationPage;
import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.port.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: lista las conversaciones del actor con keyset pagination
@Service
public class ListConversationsUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final ChannelRepository channels;

    public ListConversationsUseCase(ChannelRepository channels) {
        this.channels = channels;
    }

    @Transactional(readOnly = true)
    public ConversationPage execute(Cursor after, Integer requestedSize) {
        // acotamos el tamano de pagina para no permitir descargas masivas
        int limit = clampPageSize(requestedSize);
        // la vista rw_user_conversation ya filtra por membresia del actor fijado en la transaccion
        return channels.listConversations(after, limit);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
