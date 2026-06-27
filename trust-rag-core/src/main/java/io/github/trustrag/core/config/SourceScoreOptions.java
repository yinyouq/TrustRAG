package io.github.trustrag.core.config;

import java.util.Locale;
import java.util.Map;

/**
 * SourceScoreOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
public record SourceScoreOptions(Map<String, Double> scores) {

    public SourceScoreOptions {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
        scores.forEach((name, score) -> {
            if (score == null || score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException(
                        "Source score must be between 0 and 1: " + name);
            }
        });
    }

    public double score(String sourceType) {
        String normalized = sourceType == null || sourceType.isBlank()
                ? "unknown"
                : sourceType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return scores.getOrDefault(normalized, scores.getOrDefault("unknown", 0.10));
    }
}
