package io.github.trustrag.core.config;

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
