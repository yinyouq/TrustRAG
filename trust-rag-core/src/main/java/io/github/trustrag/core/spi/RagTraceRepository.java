package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagTrace;

public interface RagTraceRepository {

    void save(RagTrace trace);

    boolean existsByTraceId(String traceId);
}
