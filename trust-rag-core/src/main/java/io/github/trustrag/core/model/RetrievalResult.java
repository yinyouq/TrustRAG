package io.github.trustrag.core.model;

import java.util.List;

/**
 * RetrievalResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record RetrievalResult(List<RetrievedChunk> chunks) {

    public RetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
