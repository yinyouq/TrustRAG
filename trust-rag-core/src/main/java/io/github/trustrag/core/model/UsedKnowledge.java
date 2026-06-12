package io.github.trustrag.core.model;

public record UsedKnowledge(
        Long knowledgeId,
        String title,
        String content,
        TrustLevel trustLevel,
        ScopeType scopeType,
        double finalScore,
        String sourceRef) {
}
