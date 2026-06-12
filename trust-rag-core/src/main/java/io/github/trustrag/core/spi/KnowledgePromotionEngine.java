package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.PromotionResult;

public interface KnowledgePromotionEngine {

    PromotionResult evaluate(long knowledgeId);

    void promoteToMedium(long knowledgeId, PromotionResult result);

    void reject(long knowledgeId, String reason);

    void markConflict(long knowledgeId, String reason);

    void markExpired(long knowledgeId, String reason);

    void markMergePending(long knowledgeId, String reason);

    void keepLow(long knowledgeId, String reason);
}
