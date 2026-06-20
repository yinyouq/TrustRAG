package io.github.trustrag.core.config;

public record ConflictDetectionOptions(
        boolean enabled,
        boolean compareWithHigh,
        boolean compareWithMedium,
        int topK,
        double similarityThreshold,
        boolean llmJudgeEnabled) {

    public ConflictDetectionOptions {
        if (topK < 0) {
            throw new IllegalArgumentException("Conflict topK cannot be negative");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "Conflict similarityThreshold must be between 0 and 1");
        }
    }
}
