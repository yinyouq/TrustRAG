package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

public interface RerankClient {

    List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks);
}
