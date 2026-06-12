package io.github.trustrag.core.model;

import java.util.List;
import java.util.Set;

public record VectorSearchRequest(
        List<Float> vector,
        ScopeContext scope,
        Set<TrustLevel> trustLevels,
        Set<KnowledgeStatus> statuses,
        int topK,
        double minScore) {

    public VectorSearchRequest {
        vector = List.copyOf(vector);
        trustLevels = Set.copyOf(trustLevels);
        statuses = Set.copyOf(statuses);
    }
}
