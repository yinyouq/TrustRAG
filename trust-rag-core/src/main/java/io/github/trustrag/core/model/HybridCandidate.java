package io.github.trustrag.core.model;

public record HybridCandidate(
        long knowledgeId,
        Double vectorScore,
        Integer vectorRank,
        Double keywordScore,
        Integer keywordRank,
        double rrfScore) {
}
