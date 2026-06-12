package io.github.trustrag.core.model;

public record DuplicateCheckResult(
        DuplicateAction action,
        Long existingKnowledgeId,
        double similarity,
        String reason) {

    public static DuplicateCheckResult none() {
        return new DuplicateCheckResult(DuplicateAction.NONE, null, 0.0, "");
    }
}
