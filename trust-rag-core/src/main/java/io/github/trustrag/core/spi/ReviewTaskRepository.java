package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ReviewTask;

import java.util.Optional;

/**
 * ReviewTaskRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface ReviewTaskRepository {

    ReviewTask save(ReviewTask task);

    void update(ReviewTask task);

    Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId);
}
