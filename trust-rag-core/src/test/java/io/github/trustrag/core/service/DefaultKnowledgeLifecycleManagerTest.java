package io.github.trustrag.core.service;

import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 DefaultKnowledgeLifecycleManager 的关键行为、边界条件和回归场景。
 */
class DefaultKnowledgeLifecycleManagerTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void expiresDueKnowledgeAndRemovesItsVector() {
        Fixture fixture = fixture();
        KnowledgeItem expired = fixture.repository.save(item(
                1L, TrustLevel.MEDIUM, KnowledgeStatus.MEDIUM_ENABLED,
                NOW.minusSeconds(1)));

        int count = fixture.manager.expireDueKnowledge();

        assertThat(count).isEqualTo(1);
        assertThat(fixture.repository.findById(expired.id()).orElseThrow().status())
                .isEqualTo(KnowledgeStatus.EXPIRED);
        assertThat(fixture.vectorStore.deleted).containsExactly(expired.id());
        assertThat(fixture.lineage.values)
                .extracting(KnowledgeLineage::action)
                .contains("KNOWLEDGE_EXPIRED");
    }

    @Test
    void retriesFailedDowngradeIndexAndRestoresIntendedMediumStatus() {
        Fixture fixture = fixture();
        KnowledgeItem high = fixture.repository.save(item(
                2L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, null));
        fixture.vectorStore.failUpsert = true;

        assertThatThrownBy(() -> fixture.manager.downgrade(
                high.id(), "admin", "source confidence dropped"))
                .isInstanceOf(IllegalStateException.class);

        KnowledgeItem failed = fixture.repository.findById(high.id()).orElseThrow();
        assertThat(failed.trustLevel()).isEqualTo(TrustLevel.MEDIUM);
        assertThat(failed.status()).isEqualTo(KnowledgeStatus.INDEX_FAILED);
        assertThat(failed.governance().promotionStage())
                .isEqualTo("INDEX_RETRY_TARGET_MEDIUM_ENABLED");

        fixture.vectorStore.failUpsert = false;
        int succeeded = fixture.manager.retryFailedIndexes();

        KnowledgeItem recovered = fixture.repository.findById(high.id()).orElseThrow();
        assertThat(succeeded).isEqualTo(1);
        assertThat(recovered.status()).isEqualTo(KnowledgeStatus.MEDIUM_ENABLED);
        assertThat(recovered.governance().promotionStage()).isNull();
        assertThat(fixture.tasks.values)
                .singleElement()
                .extracting(PromotionTask::status)
                .isEqualTo(PromotionTaskStatus.SUCCESS);
    }

    @Test
    void deletesKnowledgeByRejectingItAndRemovingItsVector() {
        Fixture fixture = fixture();
        KnowledgeItem high = fixture.repository.save(item(
                3L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, null));

        KnowledgeItem deleted = fixture.manager.delete(
                high.id(), "admin", "wrong knowledge");

        assertThat(deleted.status()).isEqualTo(KnowledgeStatus.REJECTED);
        assertThat(deleted.rejectReason()).isEqualTo("wrong knowledge");
        assertThat(fixture.repository.findById(high.id()).orElseThrow().status())
                .isEqualTo(KnowledgeStatus.REJECTED);
        assertThat(fixture.vectorStore.deleted).containsExactly(high.id());
        assertThat(fixture.lineage.values)
                .extracting(KnowledgeLineage::action)
                .contains("KNOWLEDGE_DELETED");
    }

    private Fixture fixture() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        RecordingReviewRepository reviews = new RecordingReviewRepository();
        RecordingLineageRepository lineage = new RecordingLineageRepository();
        RecordingPromotionTaskRepository tasks = new RecordingPromotionTaskRepository();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        DefaultKnowledgeLifecycleManager manager = new DefaultKnowledgeLifecycleManager(
                repository, reviews, lineage, tasks, embeddingClient(), vectorStore,
                new KnowledgeStateMachine(),
                new LifecycleOptions(30, 90, true, 3, 3),
                TransactionRunner.direct(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(repository, lineage, tasks, vectorStore, manager);
    }

    private EmbeddingClient embeddingClient() {
        return new EmbeddingClient() {
            @Override
            public List<Float> embed(String text) {
                return List.of(1.0f);
            }

            @Override
            public String modelName() {
                return "test";
            }

            @Override
            public int dimension() {
                return 1;
            }
        };
    }

    private KnowledgeItem item(
            long id,
            TrustLevel trustLevel,
            KnowledgeStatus status,
            Instant expiresAt) {
        return new KnowledgeItem(
                id, "title-" + id, "claim-" + id, "content-" + id, null, "test",
                trustLevel, status, ScopeType.GLOBAL, null, null, null, null,
                "official_doc", "source-" + id, "evidence",
                Long.toString(id), "test", 1, 1.0, 0.0, 1, "hash-" + id,
                null, null, null, NOW, NOW, expiresAt);
    }

    private record Fixture(
            TestKnowledgeRepository repository,
            RecordingLineageRepository lineage,
            RecordingPromotionTaskRepository tasks,
            RecordingVectorStore vectorStore,
            DefaultKnowledgeLifecycleManager manager) {
    }

    private static final class RecordingVectorStore implements KnowledgeVectorStore {
        private final List<Long> deleted = new ArrayList<>();
        private boolean failUpsert;

        @Override
        public void initialize() {
        }

        @Override
        public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            if (failUpsert) {
                throw new IllegalStateException("simulated vector failure");
            }
        }

        @Override
        public void delete(long knowledgeId) {
            deleted.add(knowledgeId);
        }

        @Override
        public List<VectorHit> search(VectorSearchRequest request) {
            return List.of();
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
            return values.stream()
                    .filter(value -> value.knowledgeId() == knowledgeId)
                    .toList();
        }
    }

    private static final class RecordingReviewRepository implements ReviewTaskRepository {
        private ReviewTask pending;

        @Override
        public ReviewTask save(ReviewTask task) {
            pending = task;
            return task;
        }

        @Override
        public void update(ReviewTask task) {
            pending = task;
        }

        @Override
        public Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId) {
            return Optional.ofNullable(pending)
                    .filter(value -> value.knowledgeId() == knowledgeId);
        }
    }

    private static final class RecordingPromotionTaskRepository implements PromotionTaskRepository {
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
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index).id().equals(task.id())) {
                    values.set(index, task);
                    return;
                }
            }
        }

        @Override
        public boolean claim(PromotionTask task, Instant startedAt) {
            Optional<PromotionTask> current = values.stream()
                    .filter(value -> value.id().equals(task.id()))
                    .findFirst();
            if (current.isEmpty()
                    || current.get().status() != task.status()
                    || current.get().retryCount() != task.retryCount()) {
                return false;
            }
            update(task.start(startedAt));
            return true;
        }

        @Override
        public Optional<PromotionTask> findActiveByKnowledgeId(
                long knowledgeId,
                PromotionTaskType taskType) {
            return values.stream()
                    .filter(value -> value.knowledgeId() == knowledgeId)
                    .filter(value -> value.taskType() == taskType)
                    .filter(value -> value.status() == PromotionTaskStatus.PENDING
                            || value.status() == PromotionTaskStatus.RUNNING
                            || value.status() == PromotionTaskStatus.FAILED)
                    .findFirst();
        }

        @Override
        public List<PromotionTask> findRunnable(int retryLimit, int limit) {
            return values.stream()
                    .filter(value -> runnable(value, retryLimit))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PromotionTask> findRunnableByType(
                PromotionTaskType taskType,
                int retryLimit,
                int limit) {
            return values.stream()
                    .filter(value -> value.taskType() == taskType)
                    .filter(value -> runnable(value, retryLimit))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PromotionTask> find(
                PromotionTaskStatus status,
                PromotionTaskType taskType,
                Long knowledgeId,
                int limit,
                int offset) {
            return values.stream()
                    .filter(value -> status == null || value.status() == status)
                    .filter(value -> taskType == null || value.taskType() == taskType)
                    .filter(value -> knowledgeId == null || value.knowledgeId() == knowledgeId)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        private boolean runnable(PromotionTask task, int retryLimit) {
            return task.status() == PromotionTaskStatus.PENDING
                    || (task.status() == PromotionTaskStatus.FAILED
                    && task.retryCount() < retryLimit);
        }
    }
}
