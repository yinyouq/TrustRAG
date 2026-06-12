package io.github.trustrag.core.config;

import java.util.Locale;
import java.util.Map;

public record SourceScoreOptions(Map<String, Double> scores) {

    public SourceScoreOptions {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }

    public double score(String sourceType) {
        String normalized = sourceType == null || sourceType.isBlank()
                ? "unknown"
                : sourceType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return scores.getOrDefault(normalized, scores.getOrDefault("unknown", 0.10));
    }
}
