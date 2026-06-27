package io.github.trustrag.core.exception;

/**
 * KnowledgeNotFoundException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public final class KnowledgeNotFoundException extends TrustRagException {

    public KnowledgeNotFoundException(long id) {
        super("Knowledge item not found: " + id);
    }
}
