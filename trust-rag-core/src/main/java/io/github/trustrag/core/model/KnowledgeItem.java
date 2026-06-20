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
        Instant expiresAt,
        KnowledgeGovernance governance,
        KnowledgeSourceMetadata sourceMetadata) {

    public KnowledgeItem(
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
        this(
                id, title, claim, content, summary, knowledgeType, trustLevel, status, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version, hash, approvedBy, approvedAt, rejectReason, createdAt, updatedAt,
                expiresAt, KnowledgeGovernance.empty(), KnowledgeSourceMetadata.empty());
    }

    public KnowledgeItem(
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
            Instant expiresAt,
            KnowledgeGovernance governance) {
        this(
                id, title, claim, content, summary, knowledgeType, trustLevel, status, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version, hash, approvedBy, approvedAt, rejectReason, createdAt, updatedAt,
                expiresAt, governance, KnowledgeSourceMetadata.empty());
    }

    public KnowledgeItem {
        governance = governance == null ? KnowledgeGovernance.empty() : governance;
        sourceMetadata = sourceMetadata == null
                ? KnowledgeSourceMetadata.empty()
                : sourceMetadata;
    }

    public KnowledgeItem withId(long newId) {
        return copy(newId, title, content, trustLevel, status, scopeType, version,
                approvedBy, approvedAt, rejectReason, embeddingId, embeddingModel,
                embeddingDimension, updatedAt, governance);
    }

    public KnowledgeItem withIndexState(
            KnowledgeStatus newStatus,
            String newEmbeddingId,
            String newEmbeddingModel,
            Integer newEmbeddingDimension,
            Instant now) {
        return copy(id, title, content, trustLevel, newStatus, scopeType, version,
                approvedBy, approvedAt, rejectReason, newEmbeddingId, newEmbeddingModel,
                newEmbeddingDimension, now, governance);
    }

    public KnowledgeItem beginHighApproval(String reviewer, String newTitle, String newContent, Instant now) {
        String effectiveTitle = newTitle == null || newTitle.isBlank() ? title : newTitle.trim();
        String effectiveContent = newContent == null || newContent.isBlank() ? content : newContent.trim();
        ScopeType approvedScope = scopeType == ScopeType.GLOBAL_CANDIDATE ? ScopeType.GLOBAL : scopeType;
        return new KnowledgeItem(
                id, effectiveTitle, claim, effectiveContent, summary, knowledgeType,
                TrustLevel.HIGH, KnowledgeStatus.INDEXING, approvedScope,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version + 1, hash, reviewer, now, null, createdAt, now, null,
                governance.withPrevious(trustLevel, status), sourceMetadata);
    }

    public KnowledgeItem promoteToMedium(
            KnowledgeGovernance evaluatedGovernance,
            String newTitle,
            String newClaim,
            Instant now) {
        ScopeType promotedScope = scopeType == ScopeType.GLOBAL_CANDIDATE
                ? ScopeType.GLOBAL
                : scopeType;
        return new KnowledgeItem(
                id,
                newTitle == null || newTitle.isBlank() ? title : newTitle.trim(),
                newClaim == null || newClaim.isBlank() ? claim : newClaim.trim(),
                content,
                summary,
                knowledgeType,
                TrustLevel.MEDIUM,
                KnowledgeStatus.MEDIUM_ENABLED,
                promotedScope,
                userId,
                conversationId,
                projectId,
                tenantId,
                sourceType,
                sourceRef,
                evidence,
                embeddingId,
                embeddingModel,
                embeddingDimension,
                confidence,
                privacyScore,
                version + 1,
                hash,
                approvedBy,
                approvedAt,
                null,
                createdAt,
                now,
                expiresAt,
                evaluatedGovernance.withPrevious(trustLevel, status),
                sourceMetadata);
    }

    public KnowledgeItem requestHumanReview(Instant now) {
        if (trustLevel != TrustLevel.MEDIUM || status != KnowledgeStatus.MEDIUM_ENABLED) {
            throw new IllegalStateException(
                    "Only MEDIUM_ENABLED knowledge can enter human review");
        }
        return withStatus(KnowledgeStatus.HUMAN_REVIEW_PENDING, now);
    }

    public KnowledgeItem reject(String reason, Instant now) {
        return transition(trustLevel, KnowledgeStatus.REJECTED, reason, now);
    }

    public KnowledgeItem transition(
            TrustLevel newTrustLevel,
            KnowledgeStatus newStatus,
            String reason,
            Instant now) {
        return copy(id, title, content, newTrustLevel, newStatus, scopeType, version + 1,
                approvedBy, approvedAt, reason, embeddingId, embeddingModel,
                embeddingDimension, now, governance.withPrevious(trustLevel, status));
    }

    public KnowledgeItem withHash(String newHash) {
        return new KnowledgeItem(
                id, title, claim, content, summary, knowledgeType, trustLevel, status, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version, newHash, approvedBy, approvedAt, rejectReason, createdAt, updatedAt,
                expiresAt, governance, sourceMetadata);
    }

    public KnowledgeItem withExpiresAt(Instant newExpiresAt, Instant now) {
        return new KnowledgeItem(
                id, title, claim, content, summary, knowledgeType, trustLevel, status, scopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                embeddingId, embeddingModel, embeddingDimension, confidence, privacyScore,
                version, hash, approvedBy, approvedAt, rejectReason, createdAt, now,
                newExpiresAt, governance, sourceMetadata);
    }

    public KnowledgeItem withGovernance(KnowledgeGovernance newGovernance, Instant now) {
        return copy(id, title, content, trustLevel, status, scopeType, version,
                approvedBy, approvedAt, rejectReason, embeddingId, embeddingModel,
                embeddingDimension, now, newGovernance);
    }

    public KnowledgeItem withStatus(KnowledgeStatus newStatus, Instant now) {
        return copy(id, title, content, trustLevel, newStatus, scopeType, version + 1,
                approvedBy, approvedAt, rejectReason, embeddingId, embeddingModel,
                embeddingDimension, now, governance.withPrevious(trustLevel, status));
    }

    public KnowledgeItem rollback(Instant now) {
        TrustLevel targetTrust = governance.previousTrustLevel();
        KnowledgeStatus targetStatus = governance.previousStatus();
        if (targetTrust == null || targetStatus == null) {
            throw new IllegalStateException("Knowledge item has no previous state to roll back");
        }
        return copy(id, title, content, targetTrust, KnowledgeStatus.ROLLBACK, scopeType, version + 1,
                approvedBy, approvedAt, "rollback to " + targetStatus, embeddingId, embeddingModel,
                embeddingDimension, now, governance);
    }

    public KnowledgeItem restorePrevious(Instant now) {
        TrustLevel targetTrust = governance.previousTrustLevel();
        KnowledgeStatus targetStatus = governance.previousStatus();
        if (targetTrust == null || targetStatus == null) {
            throw new IllegalStateException("Knowledge item has no previous state to restore");
        }
        return copy(id, title, content, targetTrust, targetStatus, scopeType, version + 1,
                approvedBy, approvedAt, "restored previous state", embeddingId, embeddingModel,
                embeddingDimension, now, governance.withPrevious(trustLevel, status));
    }

    private KnowledgeItem copy(
            Long newId,
            String newTitle,
            String newContent,
            TrustLevel newTrustLevel,
            KnowledgeStatus newStatus,
            ScopeType newScopeType,
            int newVersion,
            String newApprovedBy,
            Instant newApprovedAt,
            String newRejectReason,
            String newEmbeddingId,
            String newEmbeddingModel,
            Integer newEmbeddingDimension,
            Instant newUpdatedAt,
            KnowledgeGovernance newGovernance) {
        return new KnowledgeItem(
                newId, newTitle, claim, newContent, summary, knowledgeType,
                newTrustLevel, newStatus, newScopeType,
                userId, conversationId, projectId, tenantId, sourceType, sourceRef, evidence,
                newEmbeddingId, newEmbeddingModel, newEmbeddingDimension, confidence, privacyScore,
                newVersion, hash, newApprovedBy, newApprovedAt, newRejectReason,
                createdAt, newUpdatedAt, expiresAt, newGovernance, sourceMetadata);
    }
}
