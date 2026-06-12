package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

@FunctionalInterface
public interface PromptCustomizer {

    String customize(String prompt, RagRequest request, List<RetrievedChunk> chunks);
}
