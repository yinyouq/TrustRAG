package io.github.trustrag.core.model;

/**
 * PromotionAction 是领域枚举，限定相关状态、动作或类型的合法取值。
 */
public enum PromotionAction {
    PROMOTE_TO_MEDIUM,
    KEEP_LOW,
    REJECT,
    MARK_CONFLICT,
    MARK_EXPIRED,
    MERGE_PENDING
}
