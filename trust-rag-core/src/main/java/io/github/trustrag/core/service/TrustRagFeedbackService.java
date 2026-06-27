package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.RagFeedbackRequest;

import java.util.Optional;

/**
 * TrustRagFeedbackService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public interface TrustRagFeedbackService {

    Optional<KnowledgeItem> submitFeedback(RagFeedbackRequest request);
}
