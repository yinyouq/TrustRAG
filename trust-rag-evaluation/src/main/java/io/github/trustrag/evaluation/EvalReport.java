package io.github.trustrag.evaluation;

import java.time.Instant;

/**
 * EvalReport 表示评估或对比报告的结构化结果。
 */
public record EvalReport(
        Long id,
        Long evalRunId,
        Long datasetId,
        int totalCount,
        int successCount,
        int failedCount,
        Double avgRecallAt5,
        Double avgRecallAt10,
        Double avgPrecisionAt5,
        Double avgPrecisionAt10,
        Double avgMrr,
        Double avgNdcgAt5,
        Double avgNdcgAt10,
        Double avgFaithfulness,
        Double avgAnswerCorrectness,
        Double avgAnswerRelevance,
        Double avgHallucinationScore,
        Double avgLatencyMs,
        Double avgLatencyWithJudgeMs,
        Double p90LatencyMs,
        Double p90LatencyWithJudgeMs,
        Double p95LatencyMs,
        Double p95LatencyWithJudgeMs,
        Double p99LatencyMs,
        Double p99LatencyWithJudgeMs,
        String summaryJson,
        Instant createdAt) {

    public EvalReport withId(Long value) {
        return new EvalReport(
                value, evalRunId, datasetId, totalCount, successCount, failedCount,
                avgRecallAt5, avgRecallAt10, avgPrecisionAt5, avgPrecisionAt10,
                avgMrr, avgNdcgAt5, avgNdcgAt10, avgFaithfulness, avgAnswerCorrectness,
                avgAnswerRelevance, avgHallucinationScore, avgLatencyMs, avgLatencyWithJudgeMs,
                p90LatencyMs, p90LatencyWithJudgeMs, p95LatencyMs, p95LatencyWithJudgeMs,
                p99LatencyMs, p99LatencyWithJudgeMs, summaryJson, createdAt);
    }
}
