package io.github.trustrag.core.config;

public record DuplicateDetectionOptions(
        boolean enabled,
        boolean hashEnabled,
        boolean vectorEnabled,
        double similarityThreshold) {

    public DuplicateDetectionOptions {
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("Duplicate similarityThreshold must be between 0 and 1");
        }
    }
}
