package com.riwi.messaging.infrastructure.ai;

import com.riwi.messaging.application.copilot.BackfillEmbeddingsUseCase;
import com.riwi.messaging.application.copilot.BackfillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// ejecuta el backfill de embeddings al arrancar solo si riwi.embeddings.backfill-on-startup=true
@Component
@ConditionalOnProperty(prefix = "riwi.embeddings", name = "backfill-on-startup", havingValue = "true")
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final BackfillEmbeddingsUseCase backfill;

    public EmbeddingBackfillRunner(BackfillEmbeddingsUseCase backfill) {
        this.backfill = backfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        // fallo controlado: si el proveedor no esta configurado, el caso de uso hace skip sin tumbar el arranque
        try {
            BackfillResult result = backfill.execute();
            log.info("embedding backfill on startup: processed={} failed={} skipped={}",
                    result.processed(), result.failed(), result.skipped());
        } catch (RuntimeException ex) {
            log.warn("embedding backfill on startup failed: {}", ex.getMessage());
        }
    }
}
