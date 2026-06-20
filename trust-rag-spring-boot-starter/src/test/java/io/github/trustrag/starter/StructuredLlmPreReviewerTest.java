package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredLlmPreReviewerTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void includesSimilarKnowledgeAndParsesStructuredResult() {
        AtomicReference<String> prompt = new AtomicReference<>();
        StructuredLlmPreReviewer reviewer = new StructuredLlmPreReviewer(value -> {
            prompt.set(value);
            return new LlmResponse("""
                    {
                      "qualityScore": 0.9,
                      "generalValueScore": 0.8,
                      "evidenceSufficiencyScore": 0.7,
                      "riskScore": 0.1,
                      "suggestedAction": "PROMOTE_TO_MEDIUM",
                      "reason": "verified",
                      "normalizedClaim": "normalized",
                      "tags": ["tested"]
                    }
                    """, 1.0, TokenUsage.unknown());
        }, new ObjectMapper());

        var result = reviewer.review(
                item(1L, TrustLevel.LOW, KnowledgeStatus.PROMOTION_RUNNING, "candidate claim"),
                List.of(item(2L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, "existing claim")));

        assertThat(result.suggestedAction()).isEqualTo(PromotionAction.PROMOTE_TO_MEDIUM);
        assertThat(result.normalizedClaim()).isEqualTo("normalized");
        assertThat(prompt.get()).contains("[HIGH] existing claim");
    }

    @Test
    void rejectsInvalidJsonSoPromotionTaskCanRetry() {
        StructuredLlmPreReviewer reviewer = new StructuredLlmPreReviewer(
                prompt -> new LlmResponse("not-json", null, TokenUsage.unknown()),
                new ObjectMapper());

        assertThatThrownBy(() -> reviewer.review(
                item(3L, TrustLevel.LOW, KnowledgeStatus.PROMOTION_RUNNING, "candidate"),
                List.of()))
                .isInstanceOf(TrustRagException.class)
                .hasMessageContaining("Invalid LLM pre-review JSON");
    }

    private KnowledgeItem item(
            long id,
            TrustLevel trustLevel,
            KnowledgeStatus status,
            String claim) {
        return new KnowledgeItem(
                id, "title-" + id, claim, claim, null, "test",
                trustLevel, status, ScopeType.GLOBAL, null, null, null, null,
                "official_doc", "source-" + id, "evidence",
                Long.toString(id), "test", 1, 1.0, 0.0, 1, "hash-" + id,
                null, null, null, NOW, NOW, null);
    }
}
