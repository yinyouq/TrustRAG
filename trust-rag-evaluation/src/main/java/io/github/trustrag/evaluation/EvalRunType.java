package io.github.trustrag.evaluation;

/**
 * EvalRunType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum EvalRunType {
    MANUAL,
    SCHEDULED,
    BEFORE_AFTER,
    REGRESSION
}
