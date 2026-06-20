package io.github.trustrag.evaluation;

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
