package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ReviewTask;

import java.util.Optional;

public interface ReviewTaskRepository {

    ReviewTask save(ReviewTask task);

    void update(ReviewTask task);

    Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId);
}
