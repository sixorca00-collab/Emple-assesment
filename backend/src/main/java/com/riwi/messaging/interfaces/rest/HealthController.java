package com.riwi.messaging.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// health check simple y publico para verificar que el backend levanta
@Tag(name = "Infra", description = "Sondas operativas publicas")
@RestController
public class HealthController {

    // operacion publica: no requiere Bearer
    @Operation(summary = "Liveness del backend", security = {})
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
