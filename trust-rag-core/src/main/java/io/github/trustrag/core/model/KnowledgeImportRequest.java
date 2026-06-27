package io.github.trustrag.core.model;

/**
 * KnowledgeImportRequest 表示一次领域请求，承载调用方传入的业务参数。
 */
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
        String tenantId,
        KnowledgeSourceMetadata sourceMetadata) {

    public KnowledgeImportRequest(
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
        this(
                title, content, sourceType, sourceRef, trustLevel, scopeType,
                userId, conversationId, projectId, tenantId,
                KnowledgeSourceMetadata.empty());
    }

    public KnowledgeImportRequest {
        sourceMetadata = sourceMetadata == null
                ? KnowledgeSourceMetadata.empty()
                : sourceMetadata;
    }
}
