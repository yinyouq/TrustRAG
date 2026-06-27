package io.github.trustrag.document;

/**
 * DocumentImportStatus 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum DocumentImportStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PARTIAL,
    FAILED
}
