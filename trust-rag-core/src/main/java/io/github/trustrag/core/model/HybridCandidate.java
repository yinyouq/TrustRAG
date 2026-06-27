package io.github.trustrag.core.model;

/**
 * HybridCandidate 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record HybridCandidate(
        long knowledgeId,
        Double vectorScore,
        Integer vectorRank,
        Double keywordScore,
        Integer keywordRank,
        double rrfScore) {
}
