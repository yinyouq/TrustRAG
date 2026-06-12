package io.github.trustrag.core.model;

import java.util.List;

public record RetrievalResult(List<RetrievedChunk> chunks) {

    public RetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
