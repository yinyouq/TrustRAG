package io.github.trustrag.core.model;

/**
 * IndexSyncTaskStatus 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum IndexSyncTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
