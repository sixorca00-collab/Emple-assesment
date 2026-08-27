package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.CopilotHistoryPage;
import com.riwi.messaging.domain.model.CopilotQueryRecord;
import com.riwi.messaging.domain.model.CopilotUsageRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// puerto de bitacora del copiloto: persistencia de consultas/citas y reportes de consumo (Consulta 4)
public interface CopilotQueryRepository {

    // inserta la consulta y sus citas en la transaccion actual; devuelve el id generado
    UUID persist(CopilotQueryRecord record);

    // consumo acumulado por usuario; un actor no admin solo ve lo suyo (filtro en SQL)
    List<CopilotUsageRow> usage(UUID actorId, boolean isPlatformAdmin, UUID filterUserId, Instant from, Instant to);

    // historial de consultas del propio actor con keyset pagination
    CopilotHistoryPage history(UUID actorId, Cursor before, int limit);
}
