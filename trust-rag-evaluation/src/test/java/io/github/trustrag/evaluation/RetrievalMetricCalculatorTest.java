package io.github.trustrag.evaluation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 RetrievalMetricCalculator 的关键行为、边界条件和回归场景。
 */
class RetrievalMetricCalculatorTest {

    private final RetrievalMetricCalculator calculator = new RetrievalMetricCalculator();

    @Test
    void calculatesRecallPrecisionMrrAndNdcg() {
        RetrievalMetricResult result = calculator.calculate(
                List.of(10L, 2L, 30L, 1L, 40L, 3L),
                List.of(
                        expected(1L, 3),
                        expected(2L, 2),
                        expected(3L, 1)));

        assertThat(result.retrievedCount()).isEqualTo(6);
        assertThat(result.expectedKnowledgeCount()).isEqualTo(3);
        assertThat(result.recallAt5()).isEqualTo(2.0 / 3.0);
        assertThat(result.recallAt10()).isEqualTo(1.0);
        assertThat(result.precisionAt5()).isEqualTo(0.4);
        assertThat(result.precisionAt10()).isEqualTo(0.3);
        assertThat(result.mrr()).isEqualTo(0.5);
        assertThat(result.ndcgAt5()).isBetween(0.0, 1.0);
        assertThat(result.ndcgAt10()).isBetween(result.ndcgAt5(), 1.0);
    }

    @Test
    void returnsNullMetricsWhenNoExpectedKnowledgeIsConfigured() {
        RetrievalMetricResult result = calculator.calculate(List.of(1L, 2L), List.of());

        assertThat(result.retrievedCount()).isEqualTo(2);
        assertThat(result.expectedKnowledgeCount()).isZero();
        assertThat(result.recallAt5()).isNull();
        assertThat(result.precisionAt5()).isNull();
        assertThat(result.mrr()).isNull();
    }

    private ExpectedKnowledge expected(long knowledgeId, int grade) {
        return new ExpectedKnowledge(null, 1L, knowledgeId, grade, Instant.EPOCH);
    }
}
