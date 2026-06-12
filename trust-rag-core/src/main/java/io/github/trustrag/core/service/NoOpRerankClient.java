package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.spi.RerankClient;

import java.util.List;

public final class NoOpRerankClient implements RerankClient {

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        return List.copyOf(chunks);
    }
}
