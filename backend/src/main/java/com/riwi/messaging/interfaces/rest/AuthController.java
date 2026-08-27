package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.auth.LoginCommand;
import com.riwi.messaging.application.auth.LoginUseCase;
import com.riwi.messaging.application.auth.LogoutCommand;
import com.riwi.messaging.application.auth.LogoutUseCase;
import com.riwi.messaging.application.auth.RefreshCommand;
import com.riwi.messaging.application.auth.RefreshTokenUseCase;
import com.riwi.messaging.application.auth.RegisterCommand;
import com.riwi.messaging.application.auth.RegisterUserUseCase;
import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.LogoutRequest;
import com.riwi.messaging.interfaces.rest.dto.RefreshRequest;
import com.riwi.messaging.interfaces.rest.dto.RegisterRequest;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

// endpoints publicos de autenticacion
@Tag(name = "Auth", description = "Autenticacion: login, registro, rotacion de refresh y logout. Operaciones sin Bearer.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final Clock clock;

    public AuthController(LoginUseCase loginUseCase,
                          RegisterUserUseCase registerUserUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase,
                          Clock clock) {
        this.loginUseCase = loginUseCase;
        this.registerUserUseCase = registerUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.clock = clock;
    }

    // operacion publica: no requiere Bearer
    @Operation(summary = "Inicia sesion y emite un par access/refresh", security = {})
    @ApiResponse(responseCode = "200", description = "Par de tokens emitido")
    @ApiResponse(responseCode = "401", description = "Credenciales invalidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // delegamos al caso de uso; el mapeo DTO -> command es directo
        AuthTokens tokens = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(toResponse(tokens));
    }

    // operacion publica: no requiere Bearer
    @Operation(summary = "Registra una cuenta y devuelve tokens (auto-login)", security = {})
    @ApiResponse(responseCode = "201", description = "Cuenta creada y par de tokens emitido")
    @ApiResponse(responseCode = "409", description = "El correo ya pertenece a un usuario vigente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        // crea la cuenta y devuelve el mismo par de tokens que login (auto-login)
        AuthTokens tokens = registerUserUseCase.execute(new RegisterCommand(
                request.name(), request.jobTitle(), request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tokens));
    }

    // operacion publica: el refresh viaja en el body, no como Bearer
    @Operation(summary = "Rota el refresh token y emite un par nuevo", security = {})
    @ApiResponse(responseCode = "401", description = "Refresh invalido, expirado o reutilizado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        // rota el refresh y emite un par nuevo
        AuthTokens tokens = refreshTokenUseCase.execute(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(toResponse(tokens));
    }

    // operacion publica e idempotente
    @Operation(summary = "Revoca el refresh token presentado", security = {})
    @ApiResponse(responseCode = "204", description = "Refresh revocado (idempotente)")
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
