package io.github.trustrag.example;

import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ReviewRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.TrustRagEngine;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import io.github.trustrag.core.spi.KnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 KnowledgeFlywheelIntegration 的端到端集成流程，确保多个模块协作符合预期。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class KnowledgeFlywheelIntegrationTest {

    @Autowired
    private TrustRagEngine engine;

    @Autowired
    private TrustRagFeedbackService feedbackService;

    @Autowired
    private KnowledgeReviewService reviewService;

    @Autowired
    private KnowledgePromotionWorker promotionWorker;

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void correctionMovesThroughLowMediumAndHighPools() {
        var initial = engine.ask(RagRequest.builder()
                .question("Can Spring AI register tools dynamically?")
                .userId("user-1")
                .conversationId("conversation-1")
                .build());

        var candidate = feedbackService.submitFeedback(new RagFeedbackRequest(
                        initial.traceId(),
                        FeedbackType.CORRECTION,
                        "Confirmed by the application integration test.",
                        "Spring AI can register tools dynamically through ToolCallback.",
                        "user-1",
                        "conversation-1",
                        null,
                        null))
                .orElseThrow();

        assertThat(candidate.status()).isEqualTo(KnowledgeStatus.LOW_PENDING);
        assertThat(candidate.trustLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(candidate.scopeType()).isEqualTo(ScopeType.GLOBAL_CANDIDATE);
        assertThat(candidate.expiresAt()).isNotNull();

        promotionWorker.runBatch(10);
        var medium = knowledgeRepository.findById(candidate.id()).orElseThrow();
        assertThat(medium.status()).isEqualTo(KnowledgeStatus.HUMAN_REVIEW_PENDING);
        assertThat(medium.trustLevel()).isEqualTo(TrustLevel.MEDIUM);
        assertThat(medium.expiresAt()).isAfter(candidate.expiresAt());

        var approved = reviewService.approve(medium.id(), new ReviewRequest(
                "admin", "verified", null, null));

        assertThat(approved.status()).isEqualTo(KnowledgeStatus.HIGH_ENABLED);
        assertThat(approved.trustLevel()).isEqualTo(TrustLevel.HIGH);
        assertThat(approved.scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(approved.expiresAt()).isNull();

        var evolved = engine.ask(RagRequest.builder()
                .question("Can Spring AI use ToolCallback to register tools dynamically?")
                .userId("another-user")
                .build());

        assertThat(evolved.usedKnowledge())
                .anyMatch(knowledge -> knowledge.knowledgeId().equals(candidate.id())
                        && knowledge.trustLevel() == TrustLevel.HIGH);
    }

    @Test
    void adminApiIsAutoConfigured() throws Exception {
        mockMvc.perform(get("/trust-rag/admin/knowledge/candidates")
                        .queryParam("status", "HUMAN_REVIEW_PENDING")
                        .queryParam("trustLevel", "MEDIUM"))
                .andExpect(status().isOk());
    }
}
