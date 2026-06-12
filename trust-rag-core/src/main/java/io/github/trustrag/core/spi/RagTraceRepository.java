package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagTrace;

import java.util.List;

public interface RagTraceRepository {

    void save(RagTrace trace);

    boolean existsByTraceId(String traceId);

    List<Long> findUsedKnowledgeIds(String traceId);
}
