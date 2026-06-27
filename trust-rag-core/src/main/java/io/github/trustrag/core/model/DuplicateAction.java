package io.github.trustrag.core.model;

/**
 * DuplicateAction 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum DuplicateAction {
    NONE,
    REUSE_EXISTING,
    MERGE_PENDING
}
