package com.riwi.messaging.application.copilot;

import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.CopilotHistoryPage;
import com.riwi.messaging.domain.port.CopilotQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: historial de consultas del copiloto del propio actor con keyset pagination
@Service
public class GetCopilotHistoryUseCase {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CopilotQueryRepository queries;

    public GetCopilotHistoryUseCase(CopilotQueryRepository queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public CopilotHistoryPage execute(UUID actorId, Cursor before, Integer requestedSize) {
        int limit = clampPageSize(requestedSize);
        // el historial se filtra por user_id = actor en la consulta, nunca se cruza con otro usuario
        return queries.history(actorId, before, limit);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
