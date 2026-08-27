package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.PendingMessage;

import java.util.List;
import java.util.UUID;

// puerto de backfill de embeddings; se apoya en funciones SECURITY DEFINER (no dependen de RLS)
public interface EmbeddingBackfillRepository {

    // mensajes vivos sin embedding, en lote acotado
    List<PendingMessage> pending(int limit);

    // fija el embedding calculado para un mensaje
    void saveEmbedding(UUID messageId, float[] embedding);
}
