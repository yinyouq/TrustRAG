package io.github.trustrag.core.exception;

/**
 * RetrievalException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public final class RetrievalException extends TrustRagException {

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
