package io.github.trustrag.core.util;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;

/**
 * KnowledgeHashes 提供领域内部复用的工具方法，集中处理规范化和一致性逻辑。
 */
public final class KnowledgeHashes {

    private KnowledgeHashes() {
    }

    public static String scopedHash(String content, ScopeType scopeType, ScopeContext context) {
        String owner = switch (scopeType) {
            case GLOBAL, GLOBAL_CANDIDATE -> "global";
            case TENANT -> context.tenantId();
            case PROJECT -> context.projectId();
            case USER -> context.userId();
            case CONVERSATION -> context.conversationId();
        };
        return Hashing.sha256(scopeType.name() + "|" + Hashing.normalize(owner) + "|" + Hashing.normalize(content));
    }
}
