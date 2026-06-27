package io.github.trustrag.evaluation;

import java.time.Instant;

/**
 * EvalCompareReport 表示评估或对比报告的结构化结果。
 */
public record EvalCompareReport(
        Long id,
        Long beforeRunId,
        Long afterRunId,
        Double recallAt10Delta,
        Double mrrDelta,
        Double faithfulnessDelta,
        Double answerCorrectnessDelta,
        Double hallucinationScoreDelta,
        String conclusion,
        Instant createdAt) {

    public EvalCompareReport withId(Long value) {
        return new EvalCompareReport(
                value, beforeRunId, afterRunId, recallAt10Delta, mrrDelta,
                faithfulnessDelta, answerCorrectnessDelta, hallucinationScoreDelta,
                conclusion, createdAt);
    }
}
