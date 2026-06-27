package io.github.trustrag.core.config;

import java.util.List;

/**
 * GapDetectionOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
public record GapDetectionOptions(
        double lowVectorScoreThreshold,
        double lowRerankScoreThreshold,
        double gapScoreThreshold,
        List<String> uncertainExpressions) {

    public GapDetectionOptions {
        uncertainExpressions = uncertainExpressions == null ? List.of() : List.copyOf(uncertainExpressions);
    }
}
