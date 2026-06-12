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
        List<String> tags) {

    public CandidateKnowledge {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
