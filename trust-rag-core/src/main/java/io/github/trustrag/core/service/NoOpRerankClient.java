package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.spi.RerankClient;

import java.util.List;

/**
 * NoOpRerankClient 适配外部客户端能力，向核心模块暴露统一 SPI。
 */
public final class NoOpRerankClient implements RerankClient {

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        return List.copyOf(chunks);
    }
}
