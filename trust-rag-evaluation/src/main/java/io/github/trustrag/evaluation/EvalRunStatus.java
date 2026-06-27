package io.github.trustrag.evaluation;

/**
 * EvalRunStatus 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum EvalRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
