package io.github.trustrag.core.model;

import java.util.List;

public record KnowledgeImportResult(int importedCount, int duplicateCount, int failedCount, List<Long> knowledgeIds) {

    public KnowledgeImportResult {
        knowledgeIds = List.copyOf(knowledgeIds);
    }
}
