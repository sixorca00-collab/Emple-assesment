package com.riwi.messaging.application.copilot;

import com.riwi.messaging.domain.model.CopilotReadiness;
import com.riwi.messaging.domain.model.EmbeddingCoverage;
import com.riwi.messaging.domain.port.ChatPort;
import com.riwi.messaging.domain.port.EmbeddingBackfillRepository;
import com.riwi.messaging.domain.port.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// readiness del copiloto: cobertura de embeddings + un ping real a cada proveedor de IA
@Service
public class GetCopilotStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCopilotStatusUseCase.class);

    private final EmbeddingBackfillRepository ops;
    private final EmbeddingPort embeddings;
    private final ChatPort chat;
    private final CopilotModels models;

    public GetCopilotStatusUseCase(EmbeddingBackfillRepository ops,
                                   EmbeddingPort embeddings,
                                   ChatPort chat,
                                   CopilotModels models) {
        this.ops = ops;
        this.embeddings = embeddings;
        this.chat = chat;
        this.models = models;
    }

    @Transactional(readOnly = true)
    public CopilotReadiness execute() {
        // cobertura de embeddings sobre los mensajes vivos (funcion SECURITY DEFINER, conteo global)
        EmbeddingCoverage coverage = ops.coverage();
        // ping minimo a cada proveedor: llego a responder => reachable
        boolean embeddingReachable = pingEmbedding();
        boolean chatReachable = pingChat();
        return new CopilotReadiness(
                coverage.totalMessages(),
                coverage.messagesWithEmbedding(),
                models.embeddingModel(),
                models.chatModel(),
                embeddingReachable,
                chatReachable);
    }

    private boolean pingEmbedding() {
        // embedding de "ping": si el proveedor no responde 200 el adaptador lanza y devolvemos false
        try {
            return embeddings.embed("ping") != null;
        } catch (RuntimeException ex) {
            log.warn("embedding provider unreachable: {}", ex.getMessage());
            return false;
        }
    }

    private boolean pingChat() {
        // chat de un turno minimo: mismo criterio, capturamos cualquier fallo de red/credencial
        try {
            ChatPort.ChatResult result = chat.complete("Responde solo con 'ok'.", "ping");
            return result != null && result.content() != null;
        } catch (RuntimeException ex) {
            log.warn("chat provider unreachable: {}", ex.getMessage());
            return false;
        }
    }
}
