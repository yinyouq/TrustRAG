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
    }
}
