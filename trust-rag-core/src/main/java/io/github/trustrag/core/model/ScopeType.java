package io.github.trustrag.core.model;

/**
 * ScopeType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum ScopeType {
    GLOBAL,
    TENANT,
    PROJECT,
    USER,
    CONVERSATION,
    GLOBAL_CANDIDATE
}
