package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.MessagePage;
import com.riwi.messaging.domain.port.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: historial de un canal con keyset pagination (Consulta 1)
@Service
public class GetChannelHistoryUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final MessageRepository messages;

    public GetChannelHistoryUseCase(MessageRepository messages) {
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public MessagePage execute(UUID channelId, Cursor before, Integer requestedSize) {
        // acotamos el tamano de pagina
        int limit = clampPageSize(requestedSize);
        // la RLS de rw_message limita el historial a canales donde el actor es miembro
        return messages.history(channelId, before, limit);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
