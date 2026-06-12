package io.github.trustrag.core.model;

import java.util.List;

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
