package com.riwi.messaging.domain.port;

// puerto de chat del copiloto; el proveedor concreto (Groq) vive en infrastructure
public interface ChatPort {

    // completa una conversacion de un turno system + user y reporta el consumo de tokens
    ChatResult complete(String systemPrompt, String userPrompt);

    // resultado del proveedor: texto y consumo real de tokens declarado en la respuesta
    record ChatResult(String content, int promptTokens, int completionTokens, String model) {
    }
}
