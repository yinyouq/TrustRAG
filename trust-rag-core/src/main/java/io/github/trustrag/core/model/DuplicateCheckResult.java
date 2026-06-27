package io.github.trustrag.core.model;

/**
 * DuplicateCheckResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record DuplicateCheckResult(
        DuplicateAction action,
        Long existingKnowledgeId,
        double similarity,
        String reason) {

    public static DuplicateCheckResult none() {
        return new DuplicateCheckResult(DuplicateAction.NONE, null, 0.0, "");
    }
}
