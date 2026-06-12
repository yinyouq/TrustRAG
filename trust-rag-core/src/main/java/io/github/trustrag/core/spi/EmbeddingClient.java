package io.github.trustrag.core.spi;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    default List<List<Float>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    String modelName();

    int dimension();
}
