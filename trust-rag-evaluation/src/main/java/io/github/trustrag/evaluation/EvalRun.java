package io.github.trustrag.evaluation;

import java.time.Instant;

public record EvalRun(
        Long id,
        Long datasetId,
        String runName,
        EvalRunType runType,
        BeforeAfterGroup beforeAfterGroup,
        EvalRunStatus status,
        int totalCount,
        int successCount,
        int failedCount,
        String engineConfigSnapshot,
        String modelConfigSnapshot,
        Instant knowledgeSnapshotTime,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        String createdBy,
        Instant createdAt) {

    public EvalRun withId(Long value) {
        return new EvalRun(
                value, datasetId, runName, runType, beforeAfterGroup, status,
                totalCount, successCount, failedCount, engineConfigSnapshot, modelConfigSnapshot,
                knowledgeSnapshotTime, startedAt, finishedAt, errorMessage, createdBy, createdAt);
    }
}
