package io.github.trustrag.core.model;

import java.time.Instant;

public record KnowledgeLineage(
        Long id,
        long knowledgeId,
        Long parentKnowledgeId,
        String sourceTraceId,
        Long sourceFeedbackId,
        String action,
        String operatorType,
        String operatorId,
        Instant createdAt) {

    public static KnowledgeLineage system(long knowledgeId, String action, Instant now) {
        return new KnowledgeLineage(
                null, knowledgeId, null, null, null, action, "SYSTEM", "trust-rag", now);
    }
}
