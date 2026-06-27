package io.github.trustrag.core.exception;

/**
 * InvalidRagRequestException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public final class InvalidRagRequestException extends TrustRagException {

    public InvalidRagRequestException(String message) {
        super(message);
    }
}
