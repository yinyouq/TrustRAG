package io.github.trustrag.evaluation;

import java.time.Instant;
import java.util.List;

/**
 * EvalCase 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record EvalCase(
        Long id,
        Long datasetId,
        String question,
        String expectedAnswer,
        String tenantId,
        String projectId,
        String userId,
        String conversationId,
        List<String> tags,
        String difficulty,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public EvalCase {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public EvalCase withId(Long value) {
        return new EvalCase(
                value, datasetId, question, expectedAnswer, tenantId, projectId, userId,
                conversationId, tags, difficulty, enabled, createdAt, updatedAt);
    }
}
