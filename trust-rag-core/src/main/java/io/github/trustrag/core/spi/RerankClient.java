package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

/**
 * RerankClient 适配外部客户端能力，向核心模块暴露统一 SPI。
 */
public interface RerankClient {

    List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks);
}
