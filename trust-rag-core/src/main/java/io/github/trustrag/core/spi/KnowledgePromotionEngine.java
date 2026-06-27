package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.PromotionResult;

/**
 * KnowledgePromotionEngine 是领域引擎入口，编排多个服务完成一次完整业务流程。
 */
public interface KnowledgePromotionEngine {

    PromotionResult evaluate(long knowledgeId);

    void promoteToMedium(long knowledgeId, PromotionResult result);

    void reject(long knowledgeId, String reason);

    void markConflict(long knowledgeId, String reason);

    void markExpired(long knowledgeId, String reason);

    void markMergePending(long knowledgeId, String reason);

    void keepLow(long knowledgeId, String reason);
}
