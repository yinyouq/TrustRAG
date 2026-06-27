package io.github.trustrag.core.model;

import java.util.List;

/**
 * ConflictCheckResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
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
