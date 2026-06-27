package io.github.trustrag.core.model;

import java.util.List;
import java.util.Set;

/**
 * VectorSearchRequest 表示一次领域请求，承载调用方传入的业务参数。
 */
public record VectorSearchRequest(
        List<Float> vector,
        ScopeContext scope,
        Set<TrustLevel> trustLevels,
        Set<KnowledgeStatus> statuses,
        Set<ScopeType> scopeTypes,
        int topK,
        double minScore,
        boolean allowGlobalCandidate) {

    public VectorSearchRequest(
            List<Float> vector,
            ScopeContext scope,
            Set<TrustLevel> trustLevels,
            Set<KnowledgeStatus> statuses,
            int topK,
            double minScore) {
        this(vector, scope, trustLevels, statuses, Set.of(), topK, minScore, false);
    }

    public VectorSearchRequest(
            List<Float> vector,
            ScopeContext scope,
            Set<TrustLevel> trustLevels,
            Set<KnowledgeStatus> statuses,
            int topK,
            double minScore,
            boolean allowGlobalCandidate) {
        this(vector, scope, trustLevels, statuses, Set.of(), topK, minScore, allowGlobalCandidate);
    }

    public VectorSearchRequest {
        vector = List.copyOf(vector);
        trustLevels = Set.copyOf(trustLevels);
        statuses = Set.copyOf(statuses);
        scopeTypes = scopeTypes == null ? Set.of() : Set.copyOf(scopeTypes);
    }
}
