package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.EmbeddingCoverage;
import com.riwi.messaging.domain.model.PendingMessage;

import java.util.List;
import java.util.UUID;

// puerto de backfill de embeddings; se apoya en funciones SECURITY DEFINER (no dependen de RLS)
public interface EmbeddingBackfillRepository {

    // mensajes vivos sin embedding, en lote acotado
    List<PendingMessage> pending(int limit);

    // todos los mensajes vivos (modo re-embedding total: sobrescribe vectores sinteticos)
    List<PendingMessage> allLive(int limit);

    // fija el embedding calculado para un mensaje
    void saveEmbedding(UUID messageId, float[] embedding);

    // cobertura de embeddings sobre el corpus vivo (para el readiness del copiloto)
    EmbeddingCoverage coverage();
}
