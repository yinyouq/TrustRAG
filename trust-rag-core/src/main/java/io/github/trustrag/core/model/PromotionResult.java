package io.github.trustrag.core.model;

/**
 * PromotionResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record PromotionResult(
        long knowledgeId,
        PromotionAction action,
        boolean promotable,
        TrustLevel targetTrustLevel,
        KnowledgeStatus targetStatus,
        double promotionScore,
        double llmScore,
        double sourceScore,
        double evidenceScore,
        double feedbackScore,
        double usageScore,
        double conflictRisk,
        double privacyRisk,
        double staleRisk,
        String reason) {
}
