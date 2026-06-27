package io.github.trustrag.core.exception;

/**
 * TrustRagException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public class TrustRagException extends RuntimeException {

    public TrustRagException(String message) {
        super(message);
    }

    public TrustRagException(String message, Throwable cause) {
        super(message, cause);
    }
}
