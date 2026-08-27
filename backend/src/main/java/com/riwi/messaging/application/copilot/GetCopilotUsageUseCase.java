package com.riwi.messaging.application.copilot;

import com.riwi.messaging.domain.model.CopilotUsageRow;
import com.riwi.messaging.domain.port.CopilotQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// caso de uso: consumo acumulado del copiloto (Consulta 4); el filtro por actor vive en SQL
@Service
public class GetCopilotUsageUseCase {

    private final CopilotQueryRepository queries;

    public GetCopilotUsageUseCase(CopilotQueryRepository queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<CopilotUsageRow> execute(UUID actorId, boolean isPlatformAdmin, UUID filterUserId, Instant from, Instant to) {
        // un actor no admin solo ve su propio consumo aunque pida otro userId
        UUID effectiveFilter = isPlatformAdmin ? filterUserId : actorId;
        return queries.usage(actorId, isPlatformAdmin, effectiveFilter, from, to);
    }
}
