package com.riwi.messaging.support;

import com.riwi.messaging.domain.port.EmbeddingPort;

import java.util.Random;

// embedding determinista para tests: mismo texto => mismo vector, sin llamar a ninguna API
public class DeterministicEmbeddingPort implements EmbeddingPort {

    private final int dimensions;

    public DeterministicEmbeddingPort(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        // semilla estable por contenido exacto del texto
        Random random = new Random(text.hashCode());
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            // componentes en [-1, 1] para que textos distintos queden casi ortogonales
            vector[i] = random.nextFloat() * 2f - 1f;
        }
        return vector;
    }
}
