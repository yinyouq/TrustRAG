package io.github.trustrag.core.config;

import io.github.trustrag.core.model.RetrievalMode;

/**
 * HybridRetrievalOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
public record HybridRetrievalOptions(
        RetrievalMode mode,
        int fusionTopN,
        int vectorTopK,
        int keywordTopK,
        int rrfK,
        double highTrustBoost,
        double mediumTrustBoost,
        double lowTrustBoost) {

    public HybridRetrievalOptions {
        mode = mode == null ? RetrievalMode.HYBRID_RRF : mode;
        if (fusionTopN < 1 || vectorTopK < 1 || keywordTopK < 1 || rrfK < 1) {
            throw new IllegalArgumentException("Hybrid retrieval limits must be positive");
        }
        validateBoost(highTrustBoost, "highTrustBoost");
        validateBoost(mediumTrustBoost, "mediumTrustBoost");
        validateBoost(lowTrustBoost, "lowTrustBoost");
    }

    public static HybridRetrievalOptions vectorOnly(int topK) {
        return new HybridRetrievalOptions(
                RetrievalMode.VECTOR, topK, topK, topK, 60, 1.0, 0.92, 0.80);
    }

    private static void validateBoost(double value, String name) {
        if (value <= 0.0 || value > 2.0) {
            throw new IllegalArgumentException(name + " must be in (0, 2]");
        }
    }
}
