package io.github.trustrag.core.service;

import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.model.KnowledgeGapType;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
