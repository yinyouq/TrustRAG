package io.github.trustrag.core.model;

/**
 * RetrievedChunk 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record RetrievedChunk(
        long knowledgeId,
        String title,
        String content,
        String sourceRef,
        TrustLevel trustLevel,
        ScopeType scopeType,
        double vectorScore,
        Integer vectorRank,
        Double keywordScore,
        Integer keywordRank,
        double rrfScore,
        SearchType searchType,
        Double rerankScore,
        double trustScore,
        double finalScore,
        boolean usedInPrompt) {

    public RetrievedChunk(
            long knowledgeId,
            String title,
            String content,
            String sourceRef,
            TrustLevel trustLevel,
            ScopeType scopeType,
            double vectorScore,
            Double rerankScore,
            double trustScore,
            double finalScore,
            boolean usedInPrompt) {
        this(
                knowledgeId, title, content, sourceRef, trustLevel, scopeType,
                vectorScore, null, null, null, 0.0, SearchType.VECTOR_ONLY,
                rerankScore, trustScore, finalScore, usedInPrompt);
    }

    public RetrievedChunk withRerankScore(double value, double recalculatedFinalScore) {
        return new RetrievedChunk(
                knowledgeId, title, content, sourceRef, trustLevel, scopeType,
                vectorScore, vectorRank, keywordScore, keywordRank, rrfScore,
                searchType == SearchType.HYBRID_RRF
                        ? SearchType.HYBRID_RRF_RERANK
                        : searchType,
                value, trustScore, recalculatedFinalScore, usedInPrompt);
    }

    public RetrievedChunk markUsedInPrompt() {
        return new RetrievedChunk(
                knowledgeId, title, content, sourceRef, trustLevel, scopeType,
                vectorScore, vectorRank, keywordScore, keywordRank, rrfScore,
                searchType, rerankScore, trustScore, finalScore, true);
    }
}
