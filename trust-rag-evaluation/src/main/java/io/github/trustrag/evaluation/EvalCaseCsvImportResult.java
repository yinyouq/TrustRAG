package io.github.trustrag.evaluation;

import java.util.List;

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
