package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.user.GetMyProfileUseCase;
import com.riwi.messaging.application.user.MyProfileView;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint autenticado que valida el circuito completo: JWT -> SecurityContext -> aspecto RLS -> BD
@Tag(name = "Perfil", description = "Datos del usuario autenticado")
@RestController
public class MeController {

    private final GetMyProfileUseCase getMyProfileUseCase;

    public MeController(GetMyProfileUseCase getMyProfileUseCase) {
        this.getMyProfileUseCase = getMyProfileUseCase;
    }

    @Operation(summary = "Perfil del usuario autenticado y conteo de conversaciones visibles")
    @ApiResponse(responseCode = "200", description = "Perfil del actor")
    @ApiResponse(responseCode = "401", description = "Falta el Bearer o es invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal TokenClaims actor) {
        // el id sale del token, nunca de la peticion
        MyProfileView profile = getMyProfileUseCase.execute(actor.userId());
        return new MeResponse(
                profile.email(),
                profile.displayName(),
                profile.jobTitle(),
                profile.platformAdmin(),
                profile.visibleConversationCount());
    }
}
