package io.github.trustrag.core.model;

import java.util.List;

public record ConflictCheckResult(
        double conflictRisk,
        List<ConflictRecord> records,
        String reason) {

    public ConflictCheckResult {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static ConflictCheckResult none() {
        return new ConflictCheckResult(0.0, List.of(), "");
    }
}
