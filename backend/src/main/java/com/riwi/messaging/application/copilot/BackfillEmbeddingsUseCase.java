package com.riwi.messaging.application.copilot;

import com.riwi.messaging.domain.model.PendingMessage;
import com.riwi.messaging.domain.port.EmbeddingBackfillRepository;
import com.riwi.messaging.domain.port.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// caso de uso: genera y guarda los embeddings faltantes de mensajes vivos, en lote bajo demanda
@Service
public class BackfillEmbeddingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BackfillEmbeddingsUseCase.class);

    private static final int BATCH_LIMIT = 200;

    private final EmbeddingBackfillRepository backfill;
    private final EmbeddingPort embeddings;

    public BackfillEmbeddingsUseCase(EmbeddingBackfillRepository backfill, EmbeddingPort embeddings) {
        this.backfill = backfill;
        this.embeddings = embeddings;
    }

    @Transactional
    public BackfillResult execute() {
        // tomamos el lote de mensajes sin embedding (funcion SECURITY DEFINER, ve todos los canales)
        List<PendingMessage> pending = backfill.pending(BATCH_LIMIT);
        if (pending.isEmpty()) {
            return new BackfillResult(0, 0, false);
        }

        int processed = 0;
        int failed = 0;
        for (PendingMessage message : pending) {
            try {
                // llamamos al puerto de embeddings y guardamos el vector con UPDATE parametrizado
                float[] vector = embeddings.embed(message.body());
                backfill.saveEmbedding(message.messageId(), vector);
                processed++;
            } catch (RuntimeException ex) {
                // sin API key o proveedor caido: no tumbamos el proceso, registramos y saltamos
                log.warn("embedding backfill skipped: {}", ex.getMessage());
                return new BackfillResult(processed, failed, true);
            }
        }
        return new BackfillResult(processed, failed, false);
    }
}
