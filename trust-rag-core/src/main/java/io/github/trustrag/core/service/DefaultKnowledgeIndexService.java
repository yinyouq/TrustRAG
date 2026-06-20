package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.IndexOperation;
import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeVectorStore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class DefaultKnowledgeIndexService implements KnowledgeIndexService {

    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeKeywordStore keywordStore;
    private final IndexSyncTaskRepository taskRepository;
    private final Clock clock;

    public DefaultKnowledgeIndexService(
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore,
            IndexSyncTaskRepository taskRepository,
            Clock clock) {
        this.vectorStore = vectorStore;
        this.keywordStore = keywordStore;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Override
    public void upsert(KnowledgeItem knowledge, List<Float> vector) {
        List<String> failures = new ArrayList<>();
        try {
            vectorStore.upsert(knowledge, vector);
        } catch (RuntimeException exception) {
            failures.add("MILVUS: " + message(exception));
            queue(knowledge, IndexTarget.MILVUS, IndexOperation.UPSERT, exception);
        }
        if (keywordStore.available()) {
            try {
                keywordStore.upsert(knowledge);
            } catch (RuntimeException exception) {
                failures.add("OPENSEARCH: " + message(exception));
                queue(knowledge, IndexTarget.OPENSEARCH, IndexOperation.UPSERT, exception);
            }
        }
        if (!failures.isEmpty()) {
            throw new TrustRagException("Knowledge index synchronization failed: "
                    + String.join("; ", failures));
        }
    }

    @Override
    public void delete(long knowledgeId) {
        List<String> failures = new ArrayList<>();
        try {
            vectorStore.delete(knowledgeId);
        } catch (RuntimeException exception) {
            failures.add("MILVUS: " + message(exception));
            queueDelete(knowledgeId, IndexTarget.MILVUS, exception);
        }
        if (keywordStore.available()) {
            try {
                keywordStore.delete(knowledgeId);
            } catch (RuntimeException exception) {
                failures.add("OPENSEARCH: " + message(exception));
                queueDelete(knowledgeId, IndexTarget.OPENSEARCH, exception);
            }
        }
        if (!failures.isEmpty()) {
            throw new TrustRagException("Knowledge index delete failed: "
                    + String.join("; ", failures));
        }
    }

    private void queue(
            KnowledgeItem knowledge,
            IndexTarget target,
            IndexOperation operation,
            RuntimeException exception) {
        if (hasActiveTask(knowledge.id(), target, operation)) {
            return;
        }
        taskRepository.save(IndexSyncTask.pending(
                knowledge.id(), target, operation, knowledge.status(),
                abbreviate(message(exception)), clock.instant()));
    }

    private void queueDelete(
            long knowledgeId,
            IndexTarget target,
            RuntimeException exception) {
        if (hasActiveTask(knowledgeId, target, IndexOperation.DELETE)) {
            return;
        }
        taskRepository.save(IndexSyncTask.pending(
                knowledgeId, target, IndexOperation.DELETE, null,
                abbreviate(message(exception)), clock.instant()));
    }

    private boolean hasActiveTask(
            long knowledgeId,
            IndexTarget target,
            IndexOperation operation) {
        return taskRepository.findActiveByKnowledgeId(knowledgeId).stream()
                .anyMatch(task -> task.targetIndex() == target
                        && task.operation() == operation);
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private String abbreviate(String value) {
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
