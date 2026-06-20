package io.github.trustrag.core.model;

import java.util.List;

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
}
