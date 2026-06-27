package io.github.trustrag.core.model;

import java.util.List;

/**
 * GapDetectionResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record GapDetectionResult(
        boolean hasGap,
        double gapScore,
        List<KnowledgeGapType> gapTypes,
        String reason,
        boolean shouldExtractCandidate,
        boolean shouldCreateReviewTask) {

    public GapDetectionResult {
        gapTypes = gapTypes == null ? List.of() : List.copyOf(gapTypes);
    }

    public static GapDetectionResult noGap() {
        return new GapDetectionResult(false, 0.0, List.of(), "", false, false);
    }
}
