package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.auth.LoginCommand;
import com.riwi.messaging.application.auth.LoginUseCase;
import com.riwi.messaging.application.auth.LogoutCommand;
import com.riwi.messaging.application.auth.LogoutUseCase;
import com.riwi.messaging.application.auth.RefreshCommand;
import com.riwi.messaging.application.auth.RefreshTokenUseCase;
import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.LogoutRequest;
import com.riwi.messaging.interfaces.rest.dto.RefreshRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

// endpoints publicos de autenticacion
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final Clock clock;

    public AuthController(LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase,
                          Clock clock) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // delegamos al caso de uso; el mapeo DTO -> command es directo
        AuthTokens tokens = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(toResponse(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        // rota el refresh y emite un par nuevo
        AuthTokens tokens = refreshTokenUseCase.execute(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(toResponse(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        // revoca el refresh presentado (idempotente)
        logoutUseCase.execute(new LogoutCommand(request.refreshToken()));
        return ResponseEntity.noContent().build();
    }

    private TokenResponse toResponse(AuthTokens tokens) {
        long expiresIn = Duration.between(clock.instant(), tokens.accessTokenExpiresAt()).toSeconds();
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", expiresIn);
    }
}
