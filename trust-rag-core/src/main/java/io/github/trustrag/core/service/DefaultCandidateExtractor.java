package io.github.trustrag.core.service;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.CandidateExtractor;

import java.util.List;

/**
 * DefaultCandidateExtractor 承载 trust-rag-core 模块中的领域逻辑或基础设施适配职责。
 */
public final class DefaultCandidateExtractor implements CandidateExtractor {

    @Override
    public CandidateKnowledge extractCorrection(
            RagFeedbackRequest request,
            String sanitizedContent,
            ScopeType scopeType) {
        String claim = sanitizedContent.trim();
        String title = claim.length() <= 80 ? claim : claim.substring(0, 80);
        return new CandidateKnowledge(
                title,
                claim,
                claim,
                request.feedbackContent(),
                "user_correction",
                request.traceId(),
                scopeType,
                TrustLevel.LOW,
                KnowledgeStatus.LOW_PENDING,
                0.70,
                0.0,
                List.of("user-correction"));
    }
}
