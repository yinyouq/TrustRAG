package io.github.trustrag.core.service;

import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.model.KnowledgeGapType;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.SearchType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 RuleBasedKnowledgeGapDetector 的关键行为、边界条件和回归场景。
 */
class RuleBasedKnowledgeGapDetectorTest {

    private final RuleBasedKnowledgeGapDetector detector = new RuleBasedKnowledgeGapDetector(
            new GapDetectionOptions(0.65, 0.55, 0.50, List.of("insufficient information")));

    @Test
    void detectsMissingKnowledgeWithoutRetrieval() {
        RagTrace trace = RagTrace.start(
                RagRequest.builder().question("unknown").build(),
                Instant.parse("2026-06-12T00:00:00Z"));
        trace.complete(
                new LlmResponse("insufficient information", 0.1, TokenUsage.unknown()),
                null,
                10);

        var result = detector.detect(trace);

        assertThat(result.hasGap()).isTrue();
        assertThat(result.gapTypes())
                .contains(KnowledgeGapType.NO_RETRIEVAL, KnowledgeGapType.ANSWER_UNCERTAIN);
        assertThat(result.gapScore()).isEqualTo(1.0);
    }

    @Test
    void doesNotTreatKeywordOnlyFallbackAsLowVectorRecall() {
        RagTrace trace = RagTrace.start(
                RagRequest.builder().question("MilvusException code=1100").build(),
                Instant.parse("2026-06-12T00:00:00Z"));
        RetrievedChunk keywordHit = new RetrievedChunk(
                1L, "Milvus error", "diagnosis", "manual",
                TrustLevel.HIGH, ScopeType.GLOBAL,
                0.0, null, 12.0, 1, 1.0 / 61.0,
                SearchType.KEYWORD_ONLY, null, 1.0, 1.0 / 61.0, true);
        trace.retrievedChunks(List.of(keywordHit));
        trace.usedChunks(List.of(keywordHit));
        trace.complete(
                new LlmResponse("Use the documented Milvus recovery procedure.", 0.8,
                        TokenUsage.unknown()),
                null,
                10);

        var result = detector.detect(trace);

        assertThat(result.gapTypes())
                .doesNotContain(KnowledgeGapType.LOW_RETRIEVAL_SCORE);
        assertThat(result.hasGap()).isFalse();
    }
}
