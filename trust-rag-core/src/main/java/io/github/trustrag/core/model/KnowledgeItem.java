package io.github.trustrag.core.model;

import java.time.Instant;

public record KnowledgeItem(
        Long id,
        String title,
        String claim,
        String content,
        String summary,
        String knowledgeType,
        TrustLevel trustLevel,
        KnowledgeStatus status,
        ScopeType scopeType,
        String userId,
        String conversationId,
        String projectId,
        String tenantId,
        String sourceType,
        String sourceRef,
        String evidence,
        String embeddingId,
        String embeddingModel,
        Integer embeddingDimension,
        Double confidence,
        Double privacyScore,
        int version,
        String hash,
        String approvedBy,
        Instant approvedAt,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public KnowledgeItem withId(long newId) {
        return copy(newId, title, content, trustLevel, status, version, approvedBy, approvedAt, rejectReason,
                embeddingId, embeddingModel, embeddingDimension, updatedAt);
    }

    public KnowledgeItem withIndexState(
            KnowledgeStatus newStatus,
            String newEmbeddingId,
            String newEmbeddingModel,
            Integer newEmbeddingDimension,
            Instant now) {
        return copy(id, title, content, trustLevel, newStatus, version, approvedBy, approvedAt, rejectReason,
                newEmbeddingId, newEmbeddingModel, newEmbeddingDimension, now);
    }

    public KnowledgeItem approve(String reviewer, String newTitle, String newContent, Instant now) {
        String effectiveTitle = newTitle == null || newTitle.isBlank() ? title : newTitle.trim();
        String effectiveContent = newContent == null || newContent.isBlank() ? content : newContent.trim();
        ScopeType approvedScope = scopeType == ScopeType.GLOBAL_CANDIDATE ? ScopeType.GLOBAL : scopeType;
        return new KnowledgeItem(
                id, effectiveTitle, claim, effectiveContent, summary, knowledgeType,
                TrustLevel.HIGH, KnowledgeStatus.INDEXING, approvedScope,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version + 1, hash, reviewer, now, null, createdAt, now, expiresAt);
    }

    public KnowledgeItem reject(String reason, Instant now) {
        return copy(id, title, content, trustLevel, KnowledgeStatus.REJECTED, version + 1,
                approvedBy, approvedAt, reason, embeddingId, embeddingModel, embeddingDimension, now);
    }

    public KnowledgeItem withHash(String newHash) {
        return new KnowledgeItem(
                id, title, claim, content, summary, knowledgeType, trustLevel, status, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version, newHash, approvedBy, approvedAt, rejectReason, createdAt, updatedAt, expiresAt);
    }

    private KnowledgeItem copy(
            Long newId,
            String newTitle,
            String newContent,
            TrustLevel newTrustLevel,
            KnowledgeStatus newStatus,
            int newVersion,
            String newApprovedBy,
            Instant newApprovedAt,
            String newRejectReason,
            String newEmbeddingId,
            String newEmbeddingModel,
            Integer newEmbeddingDimension,
            Instant newUpdatedAt) {
        return new KnowledgeItem(
                newId, newTitle, claim, newContent, summary, knowledgeType, newTrustLevel, newStatus, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                newEmbeddingId, newEmbeddingModel, newEmbeddingDimension, confidence, privacyScore,
                newVersion, hash, newApprovedBy, newApprovedAt, newRejectReason, createdAt, newUpdatedAt, expiresAt);
    }
}
