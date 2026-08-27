package com.riwi.messaging.application.copilot;

// modo del backfill de embeddings: solo faltantes, o re-embedear todo el corpus vivo
public enum BackfillMode {

    MISSING,
    ALL;

    // tolerante: cualquier valor distinto de "all" (o nulo) cae en MISSING
    public static BackfillMode fromConfig(String raw) {
        return "all".equalsIgnoreCase(raw == null ? "" : raw.strip()) ? ALL : MISSING;
    }
}
