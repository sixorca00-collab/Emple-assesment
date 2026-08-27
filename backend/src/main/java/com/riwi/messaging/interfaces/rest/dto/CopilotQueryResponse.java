package com.riwi.messaging.interfaces.rest.dto;

import com.riwi.messaging.domain.model.CopilotAnswer;

import java.util.List;

// respuesta del copiloto: texto, estado, citas y consumo de tokens
public record CopilotQueryResponse(
        String answer,
        String status,
        List<CopilotCitationResponse> citations,
        UsageView usage
) {

    // consumo de tokens de esta consulta
    public record UsageView(int promptTokens, int completionTokens, int totalTokens) {
    }

    public static CopilotQueryResponse from(CopilotAnswer answer) {
        List<CopilotCitationResponse> citations = answer.citations().stream()
                .map(CopilotCitationResponse::from)
                .toList();
        var usage = new UsageView(
                answer.usage().promptTokens(),
                answer.usage().completionTokens(),
                answer.usage().totalTokens());
        return new CopilotQueryResponse(answer.answer(), answer.status().wire(), citations, usage);
    }
}
