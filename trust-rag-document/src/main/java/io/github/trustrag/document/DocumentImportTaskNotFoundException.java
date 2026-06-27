package io.github.trustrag.document;

import io.github.trustrag.core.exception.TrustRagException;

/**
 * DocumentImportTaskNotFoundException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public final class DocumentImportTaskNotFoundException extends TrustRagException {

    public DocumentImportTaskNotFoundException(String taskId) {
        super("Document import task not found: " + taskId);
    }
}
