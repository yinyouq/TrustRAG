package io.github.trustrag.core.model;

import java.time.Instant;

public record IndexSyncTask(
        Long id,
        long knowledgeId,
        IndexTarget targetIndex,
        IndexOperation operation,
        IndexSyncTaskStatus status,
        KnowledgeStatus targetStatus,
        int retryCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static IndexSyncTask pending(
            long knowledgeId,
            IndexTarget target,
            IndexOperation operation,
            KnowledgeStatus targetStatus,
            String error,
            Instant now) {
        return new IndexSyncTask(
                null, knowledgeId, target, operation, IndexSyncTaskStatus.PENDING,
                targetStatus, 0, error, now, null, null);
    }

    public IndexSyncTask start(Instant now) {
        return new IndexSyncTask(
                id, knowledgeId, targetIndex, operation, IndexSyncTaskStatus.RUNNING,
                targetStatus, retryCount, null, createdAt, now, null);
    }

    public IndexSyncTask succeed(Instant now) {
        return finish(IndexSyncTaskStatus.SUCCESS, null, retryCount, now);
    }

    public IndexSyncTask skip(String reason, Instant now) {
        return finish(IndexSyncTaskStatus.SKIPPED, reason, retryCount, now);
    }

    public IndexSyncTask fail(String error, Instant now) {
        return finish(IndexSyncTaskStatus.FAILED, error, retryCount + 1, now);
    }

    private IndexSyncTask finish(
            IndexSyncTaskStatus target,
            String error,
            int retries,
            Instant now) {
        return new IndexSyncTask(
                id, knowledgeId, targetIndex, operation, target,
                targetStatus, retries, error, createdAt, startedAt, now);
    }
}
