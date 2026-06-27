package io.github.trustrag.core.model;

/**
 * SearchType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum SearchType {
    VECTOR_ONLY,
    KEYWORD_ONLY,
    HYBRID_RRF,
    HYBRID_RRF_RERANK
}
