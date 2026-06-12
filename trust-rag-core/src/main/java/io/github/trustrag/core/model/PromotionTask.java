package io.github.trustrag.core.model;

import java.time.Instant;

public record PromotionTask(
        Long id,
        long knowledgeId,
        PromotionTaskStatus status,
        PromotionTaskType taskType,
        int retryCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    public static PromotionTask pending(long knowledgeId, PromotionTaskType type, Instant now) {
        return new PromotionTask(
                null, knowledgeId, PromotionTaskStatus.PENDING, type, 0,
                null, null, null, now);
    }

    public PromotionTask start(Instant now) {
        return new PromotionTask(
                id, knowledgeId, PromotionTaskStatus.RUNNING, taskType, retryCount,
                null, now, null, createdAt);
    }

    public PromotionTask succeed(Instant now) {
        return finish(PromotionTaskStatus.SUCCESS, null, now);
    }

    public PromotionTask skip(String reason, Instant now) {
        return finish(PromotionTaskStatus.SKIPPED, reason, now);
    }

    public PromotionTask fail(String error, Instant now) {
        return new PromotionTask(
                id, knowledgeId, PromotionTaskStatus.FAILED, taskType, retryCount + 1,
                error, startedAt, now, createdAt);
    }

    private PromotionTask finish(PromotionTaskStatus target, String error, Instant now) {
        return new PromotionTask(
                id, knowledgeId, target, taskType, retryCount,
                error, startedAt, now, createdAt);
    }
}
