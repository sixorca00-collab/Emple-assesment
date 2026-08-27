package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.CopilotCitation;

import java.util.UUID;

// cita a un mensaje fuente en la respuesta del copiloto
public record CopilotCitationResponse(
        UUID messageId,
        UUID channelId,
        String snippet,
        int rank
) {

    public static CopilotCitationResponse from(CopilotCitation citation) {
        return new CopilotCitationResponse(
                citation.messageId(), citation.channelId(), citation.snippet(), citation.rank());
    }
}
