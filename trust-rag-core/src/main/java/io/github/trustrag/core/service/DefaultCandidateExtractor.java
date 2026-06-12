package io.github.trustrag.core.service;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.CandidateExtractor;

import java.util.List;

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
                List.of("user-correction"));
    }
}
