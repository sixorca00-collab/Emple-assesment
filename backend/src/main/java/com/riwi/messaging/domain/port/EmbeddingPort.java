package com.riwi.messaging.domain.port;

// puerto de embeddings; el proveedor concreto (OpenAI) vive en infrastructure
public interface EmbeddingPort {

    // genera el vector de un texto; su dimension debe coincidir con rw_message.embedding
    float[] embed(String text);
}
