package io.github.trustrag.core.model;

import java.util.List;

/**
 * RagAnswer 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record RagAnswer(
        String traceId,
        String answer,
        Double confidence,
        List<UsedKnowledge> usedKnowledge,
        boolean possibleGap,
        GapDetectionResult gapDetectionResult,
        int promptTokens,
        int completionTokens,
        long latencyMs) {

    public RagAnswer {
        usedKnowledge = usedKnowledge == null ? List.of() : List.copyOf(usedKnowledge);
    }
}
