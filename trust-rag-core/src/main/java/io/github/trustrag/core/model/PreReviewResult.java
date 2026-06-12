package io.github.trustrag.core.model;

import java.util.List;

public record PreReviewResult(
        double qualityScore,
        double generalValueScore,
        double evidenceSufficiencyScore,
        double riskScore,
        PromotionAction suggestedAction,
        String reason,
        String normalizedClaim,
        List<String> tags,
        String rawJson) {

    public PreReviewResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static PreReviewResult conservative(String claim, String reason) {
        return new PreReviewResult(
                0.0, 0.0, 0.0, 1.0, PromotionAction.KEEP_LOW,
                reason, claim, List.of(), "{}");
    }
}
