package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.copilot.BackfillEmbeddingsUseCase;
import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.BackfillResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint interno de operacion; solo administradores de plataforma
@RestController
@RequestMapping("/internal/embeddings")
public class InternalEmbeddingController {

    private final BackfillEmbeddingsUseCase backfill;

    public InternalEmbeddingController(BackfillEmbeddingsUseCase backfill) {
        this.backfill = backfill;
    }

    @PostMapping("/backfill")
    public BackfillResponse backfill(@AuthenticationPrincipal TokenClaims actor) {
        // guard de rol admin (mismo criterio que el resto de rutas admin: claim del JWT)
        if (!actor.platformAdmin()) {
            throw new NotAuthorizedException("platform admin required");
        }
        // genera los embeddings faltantes en lote bajo demanda
        return BackfillResponse.from(backfill.execute());
    }
}
