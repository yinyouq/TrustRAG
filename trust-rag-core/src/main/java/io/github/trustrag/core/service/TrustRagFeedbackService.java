package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.RagFeedbackRequest;

import java.util.Optional;

public interface TrustRagFeedbackService {

    Optional<KnowledgeItem> submitFeedback(RagFeedbackRequest request);
}
