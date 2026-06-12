package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

public interface KnowledgeLifecycleManager {

    int expireDueKnowledge();

    int downgradeNegativeFeedback();

    int retryFailedIndexes();

    KnowledgeItem downgrade(long knowledgeId, String operatorId, String reason);

    KnowledgeItem rollback(long knowledgeId, String operatorId, String reason);

    KnowledgeItem merge(List<Long> sourceKnowledgeIds, long targetKnowledgeId, String operatorId);
}
