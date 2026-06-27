package io.github.trustrag.core.model;

/**
 * UsedKnowledge 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record UsedKnowledge(
        Long knowledgeId,
        String title,
        String content,
        TrustLevel trustLevel,
        ScopeType scopeType,
        double finalScore,
        String sourceRef) {
}
