package com.riwi.messaging.support;

import com.riwi.messaging.domain.port.ChatPort;

// chat falso para tests: devuelve texto fijo, cuenta tokens y guarda el ultimo prompt recibido
public class RecordingChatPort implements ChatPort {

    private volatile String lastSystemPrompt;
    private volatile String lastUserPrompt;

    @Override
    public ChatResult complete(String systemPrompt, String userPrompt) {
        this.lastSystemPrompt = systemPrompt;
        this.lastUserPrompt = userPrompt;
        // conteo determinista de tokens (aprox 1 token / 4 chars) + salida fija
        int promptTokens = Math.max(1, (systemPrompt.length() + userPrompt.length()) / 4);
        String content = "Respuesta de prueba basada en el contexto recuperado.";
        return new ChatResult(content, promptTokens, 12, "fake-chat-model");
    }

    public String lastSystemPrompt() {
        return lastSystemPrompt;
    }

    public String lastUserPrompt() {
        return lastUserPrompt;
    }
}
