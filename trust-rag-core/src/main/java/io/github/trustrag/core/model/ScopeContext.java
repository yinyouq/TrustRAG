package io.github.trustrag.core.model;

/**
 * ScopeContext 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
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
