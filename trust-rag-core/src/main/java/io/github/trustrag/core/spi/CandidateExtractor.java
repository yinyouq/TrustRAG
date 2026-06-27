package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.ScopeType;

/**
 * CandidateExtractor 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
@FunctionalInterface
public interface CandidateExtractor {

    CandidateKnowledge extractCorrection(RagFeedbackRequest request, String sanitizedContent, ScopeType scopeType);
}
