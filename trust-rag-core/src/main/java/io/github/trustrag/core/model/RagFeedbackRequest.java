package io.github.trustrag.core.model;

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
