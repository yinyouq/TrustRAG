package io.github.trustrag.core.model;

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
