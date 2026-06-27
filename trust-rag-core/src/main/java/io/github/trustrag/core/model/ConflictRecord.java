package io.github.trustrag.core.model;

import java.time.Instant;

/**
 * ConflictRecord 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record ConflictRecord(
        Long id,
        long candidateKnowledgeId,
        long existingKnowledgeId,
        ConflictType conflictType,
        double confidence,
        String reason,
        Instant createdAt) {
}
