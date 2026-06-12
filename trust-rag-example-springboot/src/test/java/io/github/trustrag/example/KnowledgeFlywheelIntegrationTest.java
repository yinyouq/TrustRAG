package io.github.trustrag.example;

import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ReviewRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.TrustRagEngine;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;

    @Test
    void correctionCanBeReviewedAndRecalledAsHighTrustKnowledge() {
        var initial = engine.ask(RagRequest.builder()
                .question("Spring AI 能不能动态注册工具？")
                .userId("user-1")
                .conversationId("conversation-1")
                .build());

        var candidate = feedbackService.submitFeedback(new RagFeedbackRequest(
                        initial.traceId(),
                        FeedbackType.CORRECTION,
                        "用户确认该能力存在",
                        "Spring AI 可以通过 ToolCallback 动态注册工具。",
                        "user-1",
                        "conversation-1",
                        null,
                        null))
                .orElseThrow();

        assertThat(candidate.status()).isEqualTo(KnowledgeStatus.PENDING_REVIEW);
        assertThat(candidate.trustLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(candidate.scopeType()).isEqualTo(ScopeType.GLOBAL_CANDIDATE);

        var approved = reviewService.approve(candidate.id(), new ReviewRequest(
                "admin", "verified", null, null));

        assertThat(approved.status()).isEqualTo(KnowledgeStatus.ENABLED);
        assertThat(approved.trustLevel()).isEqualTo(TrustLevel.HIGH);
        assertThat(approved.scopeType()).isEqualTo(ScopeType.GLOBAL);

        var evolved = engine.ask(RagRequest.builder()
                .question("Spring AI 可以通过 ToolCallback 动态注册工具吗？")
                .userId("another-user")
                .build());

        assertThat(evolved.usedKnowledge())
                .anyMatch(knowledge -> knowledge.knowledgeId().equals(candidate.id())
                        && knowledge.trustLevel() == TrustLevel.HIGH);
    }

    @Test
    void adminApiIsAutoConfigured() throws Exception {
        mockMvc.perform(get("/trust-rag/admin/knowledge/candidates")
                        .queryParam("status", "PENDING_REVIEW")
                        .queryParam("trustLevel", "LOW"))
                .andExpect(status().isOk());
    }
}
