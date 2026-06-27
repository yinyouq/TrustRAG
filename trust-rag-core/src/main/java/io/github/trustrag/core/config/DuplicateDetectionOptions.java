package io.github.trustrag.core.config;

/**
 * DuplicateDetectionOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
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
