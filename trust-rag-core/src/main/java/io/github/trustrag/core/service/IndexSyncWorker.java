package io.github.trustrag.core.service;

import io.github.trustrag.core.model.IndexOperation;
import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;

import java.time.Clock;
import java.util.List;

/**
 * 索引补偿 Worker。
 *
 * <p>当 Milvus 或 OpenSearch 写入失败时，核心流程会留下 IndexSyncTask。
 * Worker 后台重试成功后，再把关系库状态从 INDEX_FAILED 恢复到原目标状态。</p>
 */
public final class IndexSyncWorker {

    private final IndexSyncTaskRepository taskRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeKeywordStore keywordStore;
    private final TransactionRunner transactionRunner;
    private final int retryLimit;
    private final Clock clock;

    public IndexSyncWorker(
            IndexSyncTaskRepository taskRepository,
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore,
            TransactionRunner transactionRunner,
            int retryLimit,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.lineageRepository = lineageRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.keywordStore = keywordStore;
        this.transactionRunner = transactionRunner;
        this.retryLimit = retryLimit;
        this.clock = clock;
    }

    public int runBatch(int limit) {
        int succeeded = 0;
        for (IndexSyncTask task : taskRepository.findRunnable(retryLimit, limit)) {
            if (!taskRepository.claim(task, clock.instant())) {
                continue;
            }
            IndexSyncTask running = task.start(clock.instant());
            try {
                KnowledgeItem item = knowledgeRepository.findById(task.knowledgeId()).orElse(null);
                if (item == null) {
                    taskRepository.update(running.skip(
                            "Knowledge no longer exists", clock.instant()));
                    continue;
                }
                execute(running, item);
                taskRepository.update(running.succeed(clock.instant()));
                restoreIfComplete(running, item);
                succeeded++;
            } catch (Exception exception) {
                taskRepository.update(running.fail(
                        abbreviate(exception.getMessage()), clock.instant()));
            }
        }
        return succeeded;
    }

    private void execute(IndexSyncTask task, KnowledgeItem item) {
        if (task.operation() == IndexOperation.DELETE) {
            delete(task.targetIndex(), item.id());
            return;
        }
        if (item.status() != KnowledgeStatus.INDEX_FAILED
                && !item.status().isRetrievable()) {
            if (item.status() == KnowledgeStatus.INDEXING
                    || item.status() == KnowledgeStatus.PROMOTION_RUNNING
                    || item.status() == KnowledgeStatus.ROLLBACK) {
                throw new IllegalStateException(
                        "Knowledge is still transitioning: " + item.status());
            }
            delete(task.targetIndex(), item.id());
            return;
        }
        KnowledgeStatus targetStatus = task.targetStatus() == null
                ? recoveryTarget(item)
                : task.targetStatus();
        KnowledgeItem indexed = item.status() == KnowledgeStatus.INDEX_FAILED
                ? item.withIndexState(
                        targetStatus, Long.toString(item.id()),
                        embeddingClient.modelName(), embeddingClient.dimension(),
                        clock.instant())
                : item;
        if (task.targetIndex() == IndexTarget.MILVUS
                || task.targetIndex() == IndexTarget.BOTH) {
            upsertVector(indexed);
        }
        if (task.targetIndex() == IndexTarget.OPENSEARCH
                || task.targetIndex() == IndexTarget.BOTH) {
            keywordStore.upsert(indexed);
        }
    }

    private void delete(IndexTarget target, long knowledgeId) {
        if (target == IndexTarget.MILVUS || target == IndexTarget.BOTH) {
            vectorStore.delete(knowledgeId);
        }
        if (target == IndexTarget.OPENSEARCH || target == IndexTarget.BOTH) {
            keywordStore.delete(knowledgeId);
        }
    }

    private void upsertVector(KnowledgeItem indexed) {
        List<Float> vector = embeddingClient.embed(indexed.content());
        if (vector == null || vector.size() != embeddingClient.dimension()) {
            throw new IllegalStateException("Embedding dimension mismatch");
        }
        vectorStore.upsert(indexed, vector);
    }

    private void restoreIfComplete(IndexSyncTask task, KnowledgeItem original) {
        if (task.operation() == IndexOperation.DELETE
                || !taskRepository.findActiveByKnowledgeId(original.id()).isEmpty()) {
            return;
        }
        KnowledgeItem current = knowledgeRepository.findById(original.id()).orElse(null);
        if (current == null || current.status() != KnowledgeStatus.INDEX_FAILED) {
            return;
        }
        KnowledgeStatus target = task.targetStatus() == null
                ? recoveryTarget(current)
                : task.targetStatus();
        // 只有同一知识没有其他未完成补偿任务时，才允许从 INDEX_FAILED 恢复业务状态。
        KnowledgeItem restored = current.withIndexState(
                target, Long.toString(current.id()), embeddingClient.modelName(),
                embeddingClient.dimension(), clock.instant())
                .withGovernance(
                        current.governance().withStage(null),
                        clock.instant());
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    restored, KnowledgeStatus.INDEX_FAILED, current.version())) {
                return;
            }
            if (target == KnowledgeStatus.HUMAN_REVIEW_PENDING
                    && reviewTaskRepository.findPendingByKnowledgeId(current.id()).isEmpty()) {
                reviewTaskRepository.save(ReviewTask.pending(current.id(), clock.instant()));
            }
            if (target == KnowledgeStatus.HIGH_ENABLED) {
                reviewTaskRepository.findPendingByKnowledgeId(current.id())
                        .ifPresent(reviewTask -> reviewTaskRepository.update(
                                reviewTask.complete(
                                        ReviewStatus.APPROVED,
                                        current.approvedBy() == null
                                                ? "trust-rag"
                                                : current.approvedBy(),
                                        "index-sync-recovery",
                                        "Approval index synchronization recovered",
                                        clock.instant())));
            }
            lineageRepository.save(KnowledgeLineage.system(
                    current.id(), "INDEX_SYNC_RECOVERED", clock.instant()));
        });
    }

    private KnowledgeStatus recoveryTarget(KnowledgeItem item) {
        String stage = item.governance().promotionStage();
        if (stage != null && stage.startsWith("INDEX_RETRY_TARGET_")) {
            try {
                return KnowledgeStatus.valueOf(stage.substring("INDEX_RETRY_TARGET_".length()));
            } catch (IllegalArgumentException ignored) {
                // Fall through to trust-level defaults.
            }
        }
        return switch (item.trustLevel()) {
            case HIGH -> KnowledgeStatus.HIGH_ENABLED;
            case MEDIUM -> KnowledgeStatus.HUMAN_REVIEW_PENDING;
            case LOW -> KnowledgeStatus.LOW_ENABLED;
        };
    }

    private String abbreviate(String message) {
        String value = message == null ? "Unknown index synchronization failure" : message;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
