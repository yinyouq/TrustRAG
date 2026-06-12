package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.ScopeType;

@FunctionalInterface
public interface CandidateExtractor {

    CandidateKnowledge extractCorrection(RagFeedbackRequest request, String sanitizedContent, ScopeType scopeType);
}
