package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.user.GetMyProfileUseCase;
import com.riwi.messaging.application.user.MyProfileView;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.MeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint autenticado que valida el circuito completo: JWT -> SecurityContext -> aspecto RLS -> BD
@RestController
public class MeController {

    private final GetMyProfileUseCase getMyProfileUseCase;

    public MeController(GetMyProfileUseCase getMyProfileUseCase) {
        this.getMyProfileUseCase = getMyProfileUseCase;
    }

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
