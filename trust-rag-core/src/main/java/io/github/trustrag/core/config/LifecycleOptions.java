package io.github.trustrag.core.config;

/**
 * LifecycleOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
public record LifecycleOptions(
        int lowTtlDays,
        int mediumTtlDays,
        boolean autoExpireEnabled,
        int negativeFeedbackDowngradeThreshold,
        int indexFailedRetryLimit) {

    public LifecycleOptions {
        if (lowTtlDays < 1 || mediumTtlDays < 1
                || negativeFeedbackDowngradeThreshold < 1
                || indexFailedRetryLimit < 0) {
            throw new IllegalArgumentException("Lifecycle configuration is invalid");
        }
    }
}
