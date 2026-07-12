package io.github.trustrag.core.model;

import java.util.List;

/**
 * CandidateKnowledge 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record CandidateKnowledge(
        String title,
        String claim,
        String content,
        String evidence,
        String sourceType,
        String sourceRef,
        ScopeType scopeType,
        TrustLevel trustLevel,
        KnowledgeStatus status,
        double confidence,
        double privacyRisk,
        List<String> tags) {

    public CandidateKnowledge {
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (privacyRisk < 0.0 || privacyRisk > 1.0) {
            throw new IllegalArgumentException("privacyRisk must be between 0 and 1");
        }
    }

    public CandidateKnowledge withPrivacyRisk(double value) {
        return new CandidateKnowledge(
                title, claim, content, evidence, sourceType, sourceRef, scopeType,
                trustLevel, status, confidence, Math.max(privacyRisk, value), tags);
    }

    public CandidateKnowledge withTitle(String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        return new CandidateKnowledge(
                value.trim(), claim, content, evidence, sourceType, sourceRef, scopeType,
                trustLevel, status, confidence, privacyRisk, tags);
    }
}
