package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.RetrievedMessage;

import java.util.List;
import java.util.UUID;

// puerto de recuperacion de contexto del copiloto (Consulta 3); permisos aplicados EN SQL
public interface CopilotContextRepository {

    // top-K mensajes por similitud coseno, restringidos a canales donde actorId es miembro
    List<RetrievedMessage> retrieveForActor(UUID actorId, float[] queryEmbedding, int matchCount, double minSimilarity);

    // hay contexto suficientemente similar en algun canal, ignorando la membresia (senal de permisos)
    boolean contextExistsAnywhere(float[] queryEmbedding, double minSimilarity);
}
