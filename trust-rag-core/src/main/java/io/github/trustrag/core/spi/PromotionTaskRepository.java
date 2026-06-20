package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;

import java.util.List;
import java.util.Optional;

public interface PromotionTaskRepository {

    PromotionTask save(PromotionTask task);

    void update(PromotionTask task);

    boolean claim(PromotionTask task, java.time.Instant startedAt);

    Optional<PromotionTask> findActiveByKnowledgeId(long knowledgeId, PromotionTaskType taskType);

    List<PromotionTask> findRunnable(int retryLimit, int limit);

    List<PromotionTask> findRunnableByType(
            PromotionTaskType taskType,
            int retryLimit,
            int limit);

    List<PromotionTask> find(
            PromotionTaskStatus status,
            PromotionTaskType taskType,
            Long knowledgeId,
            int limit,
            int offset);
}
