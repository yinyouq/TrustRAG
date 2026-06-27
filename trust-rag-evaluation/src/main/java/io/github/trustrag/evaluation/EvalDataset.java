package io.github.trustrag.evaluation;

import java.time.Instant;

/**
 * EvalDataset 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
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
