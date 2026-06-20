package io.github.trustrag.core.model;

public enum KnowledgeStatus {
    INDEXING,
    LOW_PENDING,
    LOW_ENABLED,
    PROMOTION_PENDING,
    PROMOTION_RUNNING,
    MEDIUM_ENABLED,
    HUMAN_REVIEW_PENDING,
    HIGH_ENABLED,
    INDEX_FAILED,
    REJECTED,
    CONFLICT,
    EXPIRED,
    MERGE_PENDING,
    ROLLBACK;

    public boolean isRetrievable() {
        return this == LOW_ENABLED
                || this == MEDIUM_ENABLED
                || this == HUMAN_REVIEW_PENDING
                || this == HIGH_ENABLED;
    }

    public boolean isPromotionCandidate() {
        return this == LOW_PENDING
                || this == LOW_ENABLED
                || this == PROMOTION_PENDING;
    }

    public boolean isHumanReviewable() {
        return this == MEDIUM_ENABLED
                || this == HUMAN_REVIEW_PENDING
                || this == INDEX_FAILED;
    }
}
