package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.copilot.AskCopilotCommand;
import com.riwi.messaging.application.copilot.AskCopilotUseCase;
import com.riwi.messaging.application.copilot.GetCopilotHistoryUseCase;
import com.riwi.messaging.application.copilot.GetCopilotUsageUseCase;
import com.riwi.messaging.domain.model.CopilotAnswer;
import com.riwi.messaging.domain.model.CopilotHistoryPage;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.interfaces.rest.dto.CopilotHistoryResponse;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryRequest;
import com.riwi.messaging.interfaces.rest.dto.CopilotQueryResponse;
import com.riwi.messaging.interfaces.rest.dto.CopilotUsageResponse;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.support.CursorCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// endpoints del copiloto RAG; el actor sale siempre del JWT
@Tag(name = "Copiloto", description = "Copiloto RAG: recuperacion limitada por permisos, historial y consumo de tokens")
@RestController
@RequestMapping("/copilot")
public class CopilotController {

    private final AskCopilotUseCase askCopilot;
    private final GetCopilotUsageUseCase getUsage;
    private final GetCopilotHistoryUseCase getHistory;

    public CopilotController(AskCopilotUseCase askCopilot,
                            GetCopilotUsageUseCase getUsage,
                            GetCopilotHistoryUseCase getHistory) {
        this.askCopilot = askCopilot;
        this.getUsage = getUsage;
        this.getHistory = getHistory;
    }

    @Operation(summary = "Pregunta al copiloto; solo usa contexto de canales donde el actor es miembro")
    @ApiResponse(responseCode = "200", description = "Respuesta con citas, o negativa honesta si no hay contexto")
    @ApiResponse(responseCode = "401", description = "Falta el Bearer o es invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/query")
    public CopilotQueryResponse query(@AuthenticationPrincipal TokenClaims actor,
                                      @Valid @RequestBody CopilotQueryRequest request) {
        // recuperacion con permisos en SQL + negativa honesta + persistencia transaccional
        CopilotAnswer answer = askCopilot.execute(actor.userId(), new AskCopilotCommand(request.question()));
        return CopilotQueryResponse.from(answer);
    }

    @Operation(summary = "Consumo de tokens del copiloto; el actor ve lo suyo, platform admin desglosa por usuario")
    @GetMapping("/usage")
    public List<CopilotUsageResponse> usage(@AuthenticationPrincipal TokenClaims actor,
                                            @RequestParam(required = false) UUID userId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to) {
        // Consulta 4: el actor ve lo suyo; is_platform_admin puede desglosar por usuario
        return getUsage.execute(actor.userId(), actor.platformAdmin(), userId, from, to).stream()
                .map(CopilotUsageResponse::from)
                .toList();
    }

    @Operation(summary = "Historial de consultas del propio actor con keyset pagination")
    @GetMapping("/history")
    public PageResponse<CopilotHistoryResponse> history(@AuthenticationPrincipal TokenClaims actor,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer size) {
        // historial del propio actor con keyset (mismo patron de cursor (created_at, id))
        CopilotHistoryPage page = getHistory.execute(actor.userId(), CursorCodec.decode(cursor), size);
        List<CopilotHistoryResponse> items = page.items().stream().map(CopilotHistoryResponse::from).toList();
        return new PageResponse<>(items, CursorCodec.encode(page.nextCursor()));
    }
}
