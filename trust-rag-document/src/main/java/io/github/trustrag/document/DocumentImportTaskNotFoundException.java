package io.github.trustrag.document;

import io.github.trustrag.core.exception.TrustRagException;

public final class DocumentImportTaskNotFoundException extends TrustRagException {

    public DocumentImportTaskNotFoundException(String taskId) {
        super("Document import task not found: " + taskId);
    }
}
