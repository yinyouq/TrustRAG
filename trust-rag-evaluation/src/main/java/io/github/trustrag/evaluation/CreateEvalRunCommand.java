package io.github.trustrag.evaluation;

/**
 * CreateEvalRunCommand 表示一次命令式操作，封装创建或执行流程所需参数。
 */
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
