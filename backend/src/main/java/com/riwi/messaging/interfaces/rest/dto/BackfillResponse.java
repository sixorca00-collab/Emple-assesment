package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.application.copilot.BackfillResult;

// resultado del backfill de embeddings bajo demanda
public record BackfillResponse(
        int processed,
        int failed,
        boolean skipped
) {

    public static BackfillResponse from(BackfillResult result) {
        return new BackfillResponse(result.processed(), result.failed(), result.skipped());
    }
}
