package io.github.trustrag.evaluation;

import java.time.Instant;

public record EvalDataset(
        Long id,
        String name,
        String description,
        String tenantId,
        String projectId,
        String createdBy,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public EvalDataset withId(Long value) {
        return new EvalDataset(
                value, name, description, tenantId, projectId, createdBy,
                enabled, createdAt, updatedAt);
    }
}
