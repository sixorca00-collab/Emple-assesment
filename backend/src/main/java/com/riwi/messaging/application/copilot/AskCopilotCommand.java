package com.riwi.messaging.application.copilot;

// entrada del caso de uso del copiloto; el actor NO viaja aqui, se toma del JWT
public record AskCopilotCommand(String question) {
}
