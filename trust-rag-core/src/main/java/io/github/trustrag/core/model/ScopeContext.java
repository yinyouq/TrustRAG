package io.github.trustrag.core.model;

public record ScopeContext(
        String userId,
        String conversationId,
        String projectId,
        String tenantId) {

    public static ScopeContext from(RagRequest request) {
        return new ScopeContext(
                request.userId(),
                request.conversationId(),
                request.projectId(),
                request.tenantId());
    }
}
