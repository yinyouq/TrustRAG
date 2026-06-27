package io.github.trustrag.core.model;

/**
 * KnowledgeGapType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum KnowledgeGapType {
    NO_RETRIEVAL,
    LOW_RETRIEVAL_SCORE,
    LOW_RERANK_SCORE,
    INSUFFICIENT_CONTEXT,
    ONLY_LOW_TRUST_MATCHED,
    ANSWER_UNCERTAIN,
    USER_CORRECTION,
    NEGATIVE_FEEDBACK,
    TOOL_RESULT_MISSING,
    CONFLICT_FOUND,
    KNOWLEDGE_OUTDATED
}
