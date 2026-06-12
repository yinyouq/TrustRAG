package io.github.trustrag.core.model;

public record RetrievedChunk(
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

    public RetrievedChunk withRerankScore(double value, double recalculatedFinalScore) {
        return new RetrievedChunk(
                knowledgeId, title, content, sourceRef, trustLevel, scopeType,
                vectorScore, value, trustScore, recalculatedFinalScore, usedInPrompt);
    }

    public RetrievedChunk markUsedInPrompt() {
        return new RetrievedChunk(
                knowledgeId, title, content, sourceRef, trustLevel, scopeType,
                vectorScore, rerankScore, trustScore, finalScore, true);
    }
}
