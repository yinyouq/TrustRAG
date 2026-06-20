package io.github.trustrag.core.model;

import java.util.Set;

public record KeywordSearchRequest(
        String query,
        ScopeContext scope,
        Set<TrustLevel> trustLevels,
        Set<KnowledgeStatus> statuses,
        Set<ScopeType> scopeTypes,
        int topK,
        boolean allowGlobalCandidate) {

    public KeywordSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Keyword query must not be blank");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("Keyword topK must be positive");
        }
        trustLevels = Set.copyOf(trustLevels);
        statuses = Set.copyOf(statuses);
        scopeTypes = scopeTypes == null ? Set.of() : Set.copyOf(scopeTypes);
    }
}
