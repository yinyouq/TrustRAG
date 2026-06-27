package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.ScopeContext;

import java.util.Objects;

/**
 * KnowledgeVisibilityPolicy 定义安全或可见性策略，集中校验调用方是否满足约束。
 */
public final class KnowledgeVisibilityPolicy {

    public boolean isVisible(KnowledgeItem item, ScopeContext scope) {
        return isVisible(item, scope, false);
    }

    public boolean isVisible(KnowledgeItem item, ScopeContext scope, boolean allowGlobalCandidate) {
        return switch (item.scopeType()) {
            case GLOBAL -> item.trustLevel() != io.github.trustrag.core.model.TrustLevel.LOW;
            case GLOBAL_CANDIDATE -> allowGlobalCandidate
                    && item.trustLevel() == io.github.trustrag.core.model.TrustLevel.LOW;
            case TENANT -> sameNonBlank(item.tenantId(), scope.tenantId());
            case PROJECT -> sameNonBlank(item.projectId(), scope.projectId());
            case USER -> sameNonBlank(item.userId(), scope.userId());
            case CONVERSATION -> sameNonBlank(item.conversationId(), scope.conversationId());
        };
    }

    public void validateOwnership(KnowledgeItem item) {
        boolean valid = switch (item.scopeType()) {
            case GLOBAL, GLOBAL_CANDIDATE -> true;
            case TENANT -> hasText(item.tenantId());
            case PROJECT -> hasText(item.projectId());
            case USER -> hasText(item.userId());
            case CONVERSATION -> hasText(item.conversationId());
        };
        if (!valid) {
            throw new IllegalArgumentException("Knowledge scope " + item.scopeType() + " is missing its owner id");
        }
    }

    private static boolean sameNonBlank(String left, String right) {
        return hasText(left) && hasText(right) && Objects.equals(left, right);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
