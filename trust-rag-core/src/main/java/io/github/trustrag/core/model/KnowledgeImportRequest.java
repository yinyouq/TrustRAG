package io.github.trustrag.core.model;

public record KnowledgeImportRequest(
        String title,
        String content,
        String sourceType,
        String sourceRef,
        TrustLevel trustLevel,
        ScopeType scopeType,
        String userId,
        String conversationId,
        String projectId,
        String tenantId) {
}
