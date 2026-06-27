package io.github.trustrag.core.model;

import java.time.Instant;

/**
 * KnowledgeLineage 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
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
