package io.github.trustrag.core.exception;

public final class KnowledgeNotFoundException extends TrustRagException {

    public KnowledgeNotFoundException(long id) {
        super("Knowledge item not found: " + id);
    }
}
