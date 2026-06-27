package io.github.trustrag.evaluation;

/**
 * RetrievalMetricResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record RetrievalMetricResult(
        int retrievedCount,
        int expectedKnowledgeCount,
        Double recallAt5,
        Double recallAt10,
        Double precisionAt5,
        Double precisionAt10,
        Double mrr,
        Double ndcgAt5,
        Double ndcgAt10) {
}
