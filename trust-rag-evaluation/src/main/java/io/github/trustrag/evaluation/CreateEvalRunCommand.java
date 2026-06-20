package io.github.trustrag.evaluation;

public record CreateEvalRunCommand(
        long datasetId,
        String runName,
        EvalRunType runType,
        BeforeAfterGroup beforeAfterGroup,
        String engineConfigSnapshot,
        String modelConfigSnapshot,
        String createdBy,
        boolean async) {
}
