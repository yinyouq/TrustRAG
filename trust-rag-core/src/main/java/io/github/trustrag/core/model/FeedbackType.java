package io.github.trustrag.core.model;

/**
 * FeedbackType 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum FeedbackType {
    LIKE,
    DISLIKE,
    CORRECTION,
    IRRELEVANT_CONTEXT,
    WRONG_ANSWER,
    MISSING_KNOWLEDGE
}
