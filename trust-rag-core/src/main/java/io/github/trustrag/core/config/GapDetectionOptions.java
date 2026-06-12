package io.github.trustrag.core.config;

import java.util.List;

public record GapDetectionOptions(
        double lowVectorScoreThreshold,
        double lowRerankScoreThreshold,
        double gapScoreThreshold,
        List<String> uncertainExpressions) {

    public GapDetectionOptions {
        uncertainExpressions = uncertainExpressions == null ? List.of() : List.copyOf(uncertainExpressions);
    }
}
