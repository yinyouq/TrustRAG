package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * KnowledgeLifecycleManager 管理跨状态的领域流程，保证状态迁移和副作用一致。
 */
public interface KnowledgeLifecycleManager {

    int expireDueKnowledge();

    int downgradeNegativeFeedback();

    int retryFailedIndexes();

    KnowledgeItem downgrade(long knowledgeId, String operatorId, String reason);

    KnowledgeItem rollback(long knowledgeId, String operatorId, String reason);

    KnowledgeItem merge(List<Long> sourceKnowledgeIds, long targetKnowledgeId, String operatorId);
}
