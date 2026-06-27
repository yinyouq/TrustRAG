package io.github.trustrag.core.service;

import io.github.trustrag.core.model.IndexOperation;
import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexSyncTaskStatus;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IndexSyncWorker 的关键行为、边界条件和回归场景。
 */
class IndexSyncWorkerTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void staleUpsertTaskDeletesIndexesWhenKnowledgeWasRejected() {
        TestKnowledgeRepository knowledge = new TestKnowledgeRepository();
        knowledge.save(item(KnowledgeStatus.REJECTED));
        RecordingTasks tasks = new RecordingTasks(new IndexSyncTask(
                1L, 1L, IndexTarget.BOTH, IndexOperation.UPSERT,
                IndexSyncTaskStatus.PENDING, KnowledgeStatus.HIGH_ENABLED,
                0, "previous failure", NOW, null, null));
        RecordingVectorStore vectors = new RecordingVectorStore();
        RecordingKeywordStore keywords = new RecordingKeywordStore();
        IndexSyncWorker worker = new IndexSyncWorker(
                tasks, knowledge, reviews(), lineage(), embedding(),
                vectors, keywords, TransactionRunner.direct(), 3,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int succeeded = worker.runBatch(10);

        assertThat(succeeded).isEqualTo(1);
        assertThat(vectors.deleted).isEqualTo(1);
        assertThat(keywords.deleted).isEqualTo(1);
        assertThat(vectors.upserted).isZero();
        assertThat(keywords.upserted).isZero();
        assertThat(tasks.current.status()).isEqualTo(IndexSyncTaskStatus.SUCCESS);
    }

    private KnowledgeItem item(KnowledgeStatus status) {
        return new KnowledgeItem(
                1L, "title", "claim", "content", null, "test",
                TrustLevel.HIGH, status, ScopeType.GLOBAL,
                null, null, null, null, "official", "source", "evidence",
                "1", "test", 1, 1.0, 0.0, 1, "hash",
                null, null, null, NOW, NOW, null);
    }

    private EmbeddingClient embedding() {
        return new EmbeddingClient() {
            @Override public List<Float> embed(String text) { return List.of(1.0f); }
            @Override public String modelName() { return "test"; }
            @Override public int dimension() { return 1; }
        };
    }

    private ReviewTaskRepository reviews() {
        return new ReviewTaskRepository() {
            @Override public ReviewTask save(ReviewTask task) { return task; }
            @Override public void update(ReviewTask task) { }
            @Override public Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId) {
                return Optional.empty();
            }
        };
    }

    private KnowledgeLineageRepository lineage() {
        return new KnowledgeLineageRepository() {
            @Override public KnowledgeLineage save(KnowledgeLineage value) { return value; }
            @Override public List<KnowledgeLineage> findByKnowledgeId(long knowledgeId) {
                return List.of();
            }
        };
    }

    private static final class RecordingTasks implements IndexSyncTaskRepository {
        private IndexSyncTask current;

        private RecordingTasks(IndexSyncTask current) {
            this.current = current;
        }

        @Override public IndexSyncTask save(IndexSyncTask task) { current = task; return task; }
        @Override public void update(IndexSyncTask task) { current = task; }
        @Override public boolean claim(IndexSyncTask task, Instant startedAt) { return true; }
        @Override public List<IndexSyncTask> findRunnable(int retryLimit, int limit) {
            return current.status() == IndexSyncTaskStatus.PENDING
                    ? List.of(current)
                    : List.of();
        }
        @Override public List<IndexSyncTask> findActiveByKnowledgeId(long knowledgeId) {
            return current.status() == IndexSyncTaskStatus.SUCCESS
                    ? List.of()
                    : List.of(current);
        }
    }

    private static final class RecordingVectorStore implements KnowledgeVectorStore {
        private int upserted;
        private int deleted;
        @Override public void initialize() { }
        @Override public void upsert(KnowledgeItem knowledge, List<Float> vector) { upserted++; }
        @Override public void delete(long knowledgeId) { deleted++; }
        @Override public List<VectorHit> search(VectorSearchRequest request) { return List.of(); }
    }

    private static final class RecordingKeywordStore implements KnowledgeKeywordStore {
        private int upserted;
        private int deleted;
        @Override public void initialize() { }
        @Override public void upsert(KnowledgeItem knowledge) { upserted++; }
        @Override public void delete(long knowledgeId) { deleted++; }
        @Override public List<KeywordHit> search(KeywordSearchRequest request) { return List.of(); }
    }
}
