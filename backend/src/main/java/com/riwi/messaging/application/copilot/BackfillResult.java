package com.riwi.messaging.application.copilot;

// resultado del backfill de embeddings; skipped = true si no se pudo llamar al proveedor
public record BackfillResult(
        int processed,
        int failed,
        boolean skipped
) {
}
