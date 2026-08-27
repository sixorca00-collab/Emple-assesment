package com.riwi.messaging.domain.model;

// estado de una consulta al copiloto; el valor wire coincide con el CHECK de rw_copilot_query.status
public enum CopilotStatus {

    ANSWERED("answered"),
    REFUSED_NO_CONTEXT("refused_no_context"),
    REFUSED_PERMISSION("refused_permission"),
    ERROR("error");

    private final String wire;

    CopilotStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
