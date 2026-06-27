package io.github.trustrag.evaluation;

/**
 * EvaluationResourceConflictException 表示业务流程中的特定异常，用于向上层传递明确失败原因。
 */
public final class EvaluationResourceConflictException extends RuntimeException {

    public EvaluationResourceConflictException(String message) {
        super(message);
    }
}
