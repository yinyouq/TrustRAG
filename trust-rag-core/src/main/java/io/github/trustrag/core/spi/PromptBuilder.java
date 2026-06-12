package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

@FunctionalInterface
public interface PromptBuilder {

    String build(RagRequest request, List<RetrievedChunk> chunks);
}
