package io.github.trustrag.core.model;

/**
 * RagFeedbackRequest 表示一次领域请求，承载调用方传入的业务参数。
 */
public record RagFeedbackRequest(
        String traceId,
        FeedbackType feedbackType,
        String feedbackContent,
        String correctedAnswer,
        String userId,
        String conversationId,
        String projectId,
        String tenantId) {
}
