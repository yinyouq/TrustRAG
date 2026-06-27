package io.github.trustrag.core.model;

/**
 * ConflictType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum ConflictType {
    SUPPORT,
    CONFLICT,
    UNRELATED,
    VERSION_DIFF
}
