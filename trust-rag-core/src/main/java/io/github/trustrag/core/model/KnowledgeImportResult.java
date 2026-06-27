package io.github.trustrag.core.model;

import java.util.List;

/**
 * KnowledgeImportResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record KnowledgeImportResult(int importedCount, int duplicateCount, int failedCount, List<Long> knowledgeIds) {

    public KnowledgeImportResult {
        knowledgeIds = List.copyOf(knowledgeIds);
    }
}
