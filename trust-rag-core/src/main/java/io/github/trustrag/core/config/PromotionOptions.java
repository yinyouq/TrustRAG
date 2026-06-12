package io.github.trustrag.core.config;

public record PromotionOptions(
        boolean enabled,
        int batchSize,
        int retryLimit,
        double minPromotionScore,
        double minSourceScore,
        double minEvidenceScore,
        double maxConflictRisk,
        double maxPrivacyRisk,
        boolean llmPreReviewEnabled) {

    public PromotionOptions {
        if (batchSize < 1 || retryLimit < 0) {
            throw new IllegalArgumentException("Promotion batchSize/retryLimit is invalid");
        }
    }
}
