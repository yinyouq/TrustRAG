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
        validateScore(minPromotionScore, "minPromotionScore");
        validateScore(minSourceScore, "minSourceScore");
        validateScore(minEvidenceScore, "minEvidenceScore");
        validateScore(maxConflictRisk, "maxConflictRisk");
        validateScore(maxPrivacyRisk, "maxPrivacyRisk");
    }

    private static void validateScore(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
