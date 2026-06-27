package io.github.trustrag.evaluation;

import java.util.List;

/**
 * EvalCaseCsvImportResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record EvalCaseCsvImportResult(
        int totalRows,
        int importedRows,
        int failedRows,
        List<RowError> errors) {

    public EvalCaseCsvImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public record RowError(long rowNumber, String message) {
    }
}
