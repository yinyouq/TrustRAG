package io.github.trustrag.core.util;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;

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
