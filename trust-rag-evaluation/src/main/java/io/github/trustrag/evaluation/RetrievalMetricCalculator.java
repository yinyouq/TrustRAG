package io.github.trustrag.evaluation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索指标计算器。
 *
 * <p>支持 Recall/Precision/MRR/NDCG，输入使用知识 ID 顺序，
 * 便于直接从 RagTrace 或答案引用中计算评估结果。</p>
 */
public final class RetrievalMetricCalculator {

    public RetrievalMetricResult calculate(List<Long> retrievedKnowledgeIds, List<ExpectedKnowledge> expectedKnowledge) {
        List<Long> retrieved = retrievedKnowledgeIds == null ? List.of() : retrievedKnowledgeIds;
        List<ExpectedKnowledge> expected = expectedKnowledge == null ? List.of() : expectedKnowledge;
        Map<Long, Integer> expectedGrades = new HashMap<>();
        for (ExpectedKnowledge expectedItem : expected) {
            if (expectedItem.knowledgeId() != null) {
                // relevanceGrade 至少按 1 处理，避免 NDCG 中相关文档被当作无收益。
                expectedGrades.put(expectedItem.knowledgeId(), Math.max(1, expectedItem.relevanceGrade()));
            }
        }
        if (expectedGrades.isEmpty()) {
            return new RetrievalMetricResult(
                    retrieved.size(), 0, null, null, null, null, null, null, null);
        }
        return new RetrievalMetricResult(
                retrieved.size(),
                expectedGrades.size(),
                recallAt(retrieved, expectedGrades, 5),
                recallAt(retrieved, expectedGrades, 10),
                precisionAt(retrieved, expectedGrades, 5),
                precisionAt(retrieved, expectedGrades, 10),
                mrr(retrieved, expectedGrades),
                ndcgAt(retrieved, expectedGrades, 5),
                ndcgAt(retrieved, expectedGrades, 10));
    }

    private double recallAt(List<Long> retrieved, Map<Long, Integer> expectedGrades, int k) {
        int hits = 0;
        for (Long knowledgeId : top(retrieved, k)) {
            if (expectedGrades.containsKey(knowledgeId)) {
                hits++;
            }
        }
        return hits / (double) expectedGrades.size();
    }

    private double precisionAt(List<Long> retrieved, Map<Long, Integer> expectedGrades, int k) {
        int hits = 0;
        for (Long knowledgeId : top(retrieved, k)) {
            if (expectedGrades.containsKey(knowledgeId)) {
                hits++;
            }
        }
        return hits / (double) k;
    }

    private double mrr(List<Long> retrieved, Map<Long, Integer> expectedGrades) {
        for (int index = 0; index < retrieved.size(); index++) {
            if (expectedGrades.containsKey(retrieved.get(index))) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }

    private double ndcgAt(List<Long> retrieved, Map<Long, Integer> expectedGrades, int k) {
        double dcg = 0.0;
        List<Long> top = top(retrieved, k);
        for (int index = 0; index < top.size(); index++) {
            int grade = expectedGrades.getOrDefault(top.get(index), 0);
            dcg += gain(grade) / log2(index + 2);
        }
        // IDCG 使用期望知识的理想排序，作为当前排序质量的归一化上限。
        List<Integer> idealGrades = expectedGrades.values().stream()
                .sorted(Comparator.reverseOrder())
                .limit(k)
                .toList();
        double idcg = 0.0;
        for (int index = 0; index < idealGrades.size(); index++) {
            idcg += gain(idealGrades.get(index)) / log2(index + 2);
        }
        if (idcg == 0.0) {
            return 0.0;
        }
        return dcg / idcg;
    }

    private static List<Long> top(List<Long> values, int k) {
        if (values.size() <= k) {
            return values;
        }
        return values.subList(0, k);
    }

    private static double gain(double grade) {
        return Math.pow(2.0, grade) - 1.0;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
