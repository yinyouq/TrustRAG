package io.github.trustrag.core.model;

import java.time.Instant;

public record ConflictRecord(
        Long id,
        long candidateKnowledgeId,
        long existingKnowledgeId,
        ConflictType conflictType,
        double confidence,
        String reason,
        Instant createdAt) {
}
