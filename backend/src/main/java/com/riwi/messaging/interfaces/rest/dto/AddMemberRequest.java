package com.riwi.messaging.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// cuerpo para agregar un miembro a un canal; role es opcional ('member' por defecto)
public record AddMemberRequest(
        @NotNull UUID userId,
        String role
) {
}
