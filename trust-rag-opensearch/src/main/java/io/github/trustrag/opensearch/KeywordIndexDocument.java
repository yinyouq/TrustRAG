package io.github.trustrag.opensearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * KeywordIndexDocument 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record KeywordIndexDocument(
        @JsonProperty("knowledge_id") long knowledgeId,
        String title,
        String claim,
        String content,
        List<String> tags,
        @JsonProperty("trust_level") String trustLevel,
        @JsonProperty("scope_type") String scopeType,
        @JsonProperty("user_id") String userId,
        @JsonProperty("conversation_id") String conversationId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("tenant_id") String tenantId,
        String status,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_ref") String sourceRef,
        int version,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt) {

    static KeywordIndexDocument from(KnowledgeItem item) {
        return new KeywordIndexDocument(
                item.id(),
                item.title(),
                item.claim(),
                item.content(),
                item.governance().tags(),
                item.trustLevel().name(),
                item.scopeType().name(),
                item.userId(),
                item.conversationId(),
                item.projectId(),
                item.tenantId(),
                item.status().name(),
                item.sourceType(),
                item.sourceRef(),
                item.version(),
                item.createdAt() == null ? null : item.createdAt().toString(),
                item.updatedAt() == null ? null : item.updatedAt().toString());
    }
}
