package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionResult;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgePromotionEngine;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 KnowledgePromotionWorker 的关键行为、边界条件和回归场景。
 */
class KnowledgePromotionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void restoresPromotionPendingAndIncrementsRetryAfterEvaluationFailure() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        KnowledgeItem candidate = repository.save(candidate());
        RecordingTaskRepository tasks = new RecordingTaskRepository(
                PromotionTask.pending(candidate.id(), PromotionTaskType.LOW_TO_MEDIUM, NOW));
        RecordingLineageRepository lineage = new RecordingLineageRepository();
        KnowledgePromotionEngine failingEngine = new FailingPromotionEngine(repository);
        KnowledgePromotionWorker worker = new KnowledgePromotionWorker(
                tasks, repository, lineage, failingEngine, 3,
                Clock.fixed(NOW, ZoneOffset.UTC));

        worker.runBatch(10);

        assertThat(tasks.task.status()).isEqualTo(PromotionTaskStatus.FAILED);
        assertThat(tasks.task.retryCount()).isEqualTo(1);
        assertThat(repository.findById(candidate.id()).orElseThrow().status())
                .isEqualTo(KnowledgeStatus.PROMOTION_PENDING);
        assertThat(lineage.values)
                .extracting(KnowledgeLineage::action)
                .containsExactly("PROMOTION_RETRY_QUEUED");
    }

    private KnowledgeItem candidate() {
        return new KnowledgeItem(
                1L, "candidate", "claim", "content", null, "candidate",
                TrustLevel.LOW, KnowledgeStatus.PROMOTION_PENDING,
                ScopeType.GLOBAL_CANDIDATE, null, null, null, null,
                "user_correction", "trace-1", "evidence",
                null, null, null, 0.8, 0.0, 1, "hash-1",
                null, null, null, NOW, NOW, null);
    }

    private static final class FailingPromotionEngine implements KnowledgePromotionEngine {
        private final TestKnowledgeRepository repository;

        private FailingPromotionEngine(TestKnowledgeRepository repository) {
            this.repository = repository;
        }

        @Override
        public PromotionResult evaluate(long knowledgeId) {
            KnowledgeItem item = repository.findById(knowledgeId).orElseThrow();
            repository.update(item.withStatus(KnowledgeStatus.PROMOTION_RUNNING, NOW));
            throw new IllegalStateException("invalid pre-review JSON");
        }

        @Override
        public void promoteToMedium(long knowledgeId, PromotionResult result) {
        }

        @Override
        public void reject(long knowledgeId, String reason) {
        }

        @Override
        public void markConflict(long knowledgeId, String reason) {
        }

        @Override
        public void markExpired(long knowledgeId, String reason) {
        }

        @Override
        public void markMergePending(long knowledgeId, String reason) {
        }

        @Override
        public void keepLow(long knowledgeId, String reason) {
        }
    }

    private static final class RecordingTaskRepository implements PromotionTaskRepository {
        private PromotionTask task;

        private RecordingTaskRepository(PromotionTask task) {
            this.task = new PromotionTask(
                    1L, task.knowledgeId(), task.status(), task.taskType(),
                    task.retryCount(), task.errorMessage(), task.startedAt(),
                    task.finishedAt(), task.createdAt());
        }

        @Override
        public PromotionTask save(PromotionTask value) {
            task = value;
            return value;
        }

        @Override
        public void update(PromotionTask value) {
            task = value;
        }

        @Override
        public boolean claim(PromotionTask value, Instant startedAt) {
            if (task.status() != value.status() || task.retryCount() != value.retryCount()) {
                return false;
            }
            task = value.start(startedAt);
            return true;
        }

        @Override
        public Optional<PromotionTask> findActiveByKnowledgeId(
                long knowledgeId,
                PromotionTaskType taskType) {
            return Optional.of(task);
        }

        @Override
        public List<PromotionTask> findRunnable(int retryLimit, int limit) {
            return List.of(task);
        }

        @Override
        public List<PromotionTask> findRunnableByType(
                PromotionTaskType taskType,
                int retryLimit,
                int limit) {
            return task.taskType() == taskType
                    && task.retryCount() < retryLimit
                    && (task.status() == PromotionTaskStatus.PENDING
                    || task.status() == PromotionTaskStatus.FAILED)
                    ? List.of(task)
                    : List.of();
        }

        @Override
        public List<PromotionTask> find(
                PromotionTaskStatus status,
                PromotionTaskType taskType,
                Long knowledgeId,
                int limit,
                int offset) {
            return List.of(task);
        }
    }

    private static final class RecordingLineageRepository implements KnowledgeLineageRepository {
        private final List<KnowledgeLineage> values = new ArrayList<>();

        @Override
        public KnowledgeLineage save(KnowledgeLineage lineage) {
            values.add(lineage);
            return lineage;
        }

        @Override
        public List<KnowledgeLineage> findByKnowledgeId(long knowledgeId) {
            return values;
        }
    }
}
