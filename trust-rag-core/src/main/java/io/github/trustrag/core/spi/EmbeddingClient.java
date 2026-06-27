package io.github.trustrag.core.spi;

import java.util.List;

/**
 * EmbeddingClient 适配外部客户端能力，向核心模块暴露统一 SPI。
 */
public interface EmbeddingClient {

    List<Float> embed(String text);

    default List<List<Float>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    String modelName();

    int dimension();
}
