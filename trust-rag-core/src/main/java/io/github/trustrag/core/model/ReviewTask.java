package io.github.trustrag.core.model;

import java.time.Instant;

public record ReviewTask(
        Long id,
        long knowledgeId,
        ReviewStatus status,
        String reviewerId,
        String reviewAction,
        String reviewComment,
        Instant createdAt,
        Instant reviewedAt) {

    public static ReviewTask pending(long knowledgeId, Instant now) {
        return new ReviewTask(null, knowledgeId, ReviewStatus.PENDING, null, null, null, now, null);
    }

    public ReviewTask complete(ReviewStatus newStatus, String reviewer, String action, String comment, Instant now) {
        return new ReviewTask(id, knowledgeId, newStatus, reviewer, action, comment, createdAt, now);
    }
}
