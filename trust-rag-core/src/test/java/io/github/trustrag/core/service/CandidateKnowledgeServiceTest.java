package io.github.trustrag.core.service;

import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 CandidateKnowledgeService 的关键行为、边界条件和回归场景。
 */
class CandidateKnowledgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void reusesExactCandidateAndIncrementsUsage() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        RecordingTaskRepository tasks = new RecordingTaskRepository();
        CandidateKnowledgeService service = new CandidateKnowledgeService(
                repository, tasks, new RecordingLineageRepository(),
                new KnowledgeVisibilityPolicy(),
                new LifecycleOptions(30, 90, true, 3, 3),
                TransactionRunner.direct(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        CandidateKnowledge candidate = new CandidateKnowledge(
                "title", "claim", "same content", "evidence",
                "user_correction", "trace-1", ScopeType.USER,
                TrustLevel.LOW, KnowledgeStatus.LOW_PENDING, 0.8, 0.0, List.of());
        ScopeContext scope = new ScopeContext("user-1", null, null, null);

        KnowledgeItem first = service.submit(candidate, scope);
        KnowledgeItem duplicate = service.submit(candidate, scope);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(tasks.values).hasSize(1);
        assertThat(repository.usageIncrements(first.id())).isEqualTo(1);
    }

    private static final class RecordingTaskRepository implements PromotionTaskRepository {
        private final List<PromotionTask> values = new ArrayList<>();

        @Override
        public PromotionTask save(PromotionTask task) {
            PromotionTask saved = new PromotionTask(
                    (long) values.size() + 1, task.knowledgeId(), task.status(),
                    task.taskType(), task.retryCount(), task.errorMessage(),
                    task.startedAt(), task.finishedAt(), task.createdAt());
            values.add(saved);
            return saved;
        }

        @Override
        public void update(PromotionTask task) {
        }

        @Override
        public boolean claim(PromotionTask task, Instant startedAt) {
            return false;
        }

        @Override
        public Optional<PromotionTask> findActiveByKnowledgeId(
                long knowledgeId,
                PromotionTaskType taskType) {
            return values.stream()
                    .filter(value -> value.knowledgeId() == knowledgeId)
                    .filter(value -> value.taskType() == taskType)
                    .findFirst();
        }

        @Override
        public List<PromotionTask> findRunnable(int retryLimit, int limit) {
            return List.of();
        }

        @Override
        public List<PromotionTask> findRunnableByType(
                PromotionTaskType taskType,
                int retryLimit,
                int limit) {
            return List.of();
        }

        @Override
        public List<PromotionTask> find(
                PromotionTaskStatus status,
                PromotionTaskType taskType,
                Long knowledgeId,
                int limit,
                int offset) {
            return List.of();
        }
    }

    private static final class RecordingLineageRepository implements KnowledgeLineageRepository {
        @Override
        public KnowledgeLineage save(KnowledgeLineage lineage) {
            return lineage;
        }

        @Override
        public List<KnowledgeLineage> findByKnowledgeId(long knowledgeId) {
            return List.of();
        }
    }
}
