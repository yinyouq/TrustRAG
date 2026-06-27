package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagTrace;

import java.util.List;

/**
 * RagTraceRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface RagTraceRepository {

    void save(RagTrace trace);

    boolean existsByTraceId(String traceId);

    List<Long> findUsedKnowledgeIds(String traceId);
}
