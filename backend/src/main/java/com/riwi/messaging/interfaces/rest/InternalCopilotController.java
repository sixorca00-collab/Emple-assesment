package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.copilot.GetCopilotStatusUseCase;
import com.riwi.messaging.domain.exception.NotAuthorizedException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.CopilotStatusResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint interno de operacion del copiloto; solo administradores de plataforma
@RestController
@RequestMapping("/internal/copilot")
public class InternalCopilotController {

    private final GetCopilotStatusUseCase status;

    public InternalCopilotController(GetCopilotStatusUseCase status) {
        this.status = status;
    }

    @GetMapping("/status")
    public CopilotStatusResponse status(@AuthenticationPrincipal TokenClaims actor) {
        // guard de rol admin (mismo criterio que el resto de rutas internas: claim del JWT)
        if (!actor.platformAdmin()) {
            throw new NotAuthorizedException("platform admin required");
        }
        // cobertura de embeddings + ping real a Groq y al proveedor de embeddings
        return CopilotStatusResponse.from(status.execute());
    }
}
