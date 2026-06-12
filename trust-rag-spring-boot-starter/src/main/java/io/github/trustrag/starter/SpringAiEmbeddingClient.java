package io.github.trustrag.starter;

import io.github.trustrag.core.spi.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

public final class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final int dimension;
    private final String modelName;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel, int dimension, String modelName) {
        this.embeddingModel = embeddingModel;
        this.dimension = dimension;
        this.modelName = modelName;
    }

    @Override
    public List<Float> embed(String text) {
        float[] vector = embeddingModel.embed(text);
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
