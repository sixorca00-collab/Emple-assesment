package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.copilot.BackfillEmbeddingsUseCase;
import com.riwi.messaging.application.copilot.BackfillMode;
import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.BackfillResponse;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// endpoint interno de operacion; solo administradores de plataforma
@Tag(name = "Interno", description = "Operaciones de plataforma: SOLO platform admin (claim del JWT)")
@RestController
@RequestMapping("/internal/embeddings")
public class InternalEmbeddingController {

    private final BackfillEmbeddingsUseCase backfill;

    public InternalEmbeddingController(BackfillEmbeddingsUseCase backfill) {
        this.backfill = backfill;
    }

    @Operation(summary = "Genera en lote los embeddings faltantes (solo platform admin)")
    @ApiResponse(responseCode = "200", description = "Resumen del backfill")
    @ApiResponse(responseCode = "403", description = "El actor no es platform admin",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/backfill")
    public BackfillResponse backfill(@AuthenticationPrincipal TokenClaims actor,
                                    @RequestParam(defaultValue = "missing") String mode) {
        // guard de rol admin (mismo criterio que el resto de rutas admin: claim del JWT)
        if (!actor.platformAdmin()) {
            throw new NotAuthorizedException("platform admin required");
        }
        // mode=missing => solo faltantes; mode=all => re-embebe todo el corpus vivo
        return BackfillResponse.from(backfill.execute(BackfillMode.fromConfig(mode)));
    }
}
