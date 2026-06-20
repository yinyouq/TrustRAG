package io.github.trustrag.document;

import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;

public record DocumentImportOptions(
        String title,
        String sourceType,
        TrustLevel trustLevel,
        ScopeType scopeType,
        String userId,
        String conversationId,
        String projectId,
        String tenantId) {

    public DocumentImportOptions {
        sourceType = hasText(sourceType) ? sourceType.trim() : "document";
        trustLevel = trustLevel == null ? TrustLevel.HIGH : trustLevel;
        scopeType = scopeType == null ? ScopeType.GLOBAL : scopeType;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
