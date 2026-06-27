package io.github.trustrag.core.service;

import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 DefaultKnowledgeIndexService 的关键行为、边界条件和回归场景。
 */
class DefaultKnowledgeIndexServiceTest {

    @Test
    void queuesOnlyFailedIndexAndAvoidsDuplicateActiveTasks() {
        RecordingTasks tasks = new RecordingTasks();
        RecordingVectorStore vectors = new RecordingVectorStore();
        KnowledgeKeywordStore keywords = failingKeywordStore();
        DefaultKnowledgeIndexService service = new DefaultKnowledgeIndexService(
                vectors, keywords, tasks,
                Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC));
        KnowledgeItem item = item();

        assertThatThrownBy(() -> service.upsert(item, List.of(1.0f)))
                .hasMessageContaining("OPENSEARCH");
        assertThatThrownBy(() -> service.upsert(item, List.of(1.0f)))
                .hasMessageContaining("OPENSEARCH");

        assertThat(vectors.upserts).isEqualTo(2);
        assertThat(tasks.values).singleElement()
                .extracting(IndexSyncTask::targetIndex)
                .isEqualTo(IndexTarget.OPENSEARCH);
    }

    private KnowledgeKeywordStore failingKeywordStore() {
        return new KnowledgeKeywordStore() {
            @Override public void initialize() { }
            @Override public void upsert(KnowledgeItem knowledge) {
                throw new IllegalStateException("OpenSearch unavailable");
            }
            @Override public void delete(long knowledgeId) { }
            @Override public List<KeywordHit> search(KeywordSearchRequest request) {
                return List.of();
            }
        };
    }

    private KnowledgeItem item() {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        return new KnowledgeItem(
                1L, "title", "claim", "content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "official", "source", "evidence",
                "1", "test", 1, 1.0, 0.0, 1, "hash",
                null, null, null, now, now, null);
    }

    private static final class RecordingVectorStore implements KnowledgeVectorStore {
        private int upserts;
        @Override public void initialize() { }
        @Override public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            upserts++;
        }
        @Override public void delete(long knowledgeId) { }
        @Override public List<VectorHit> search(VectorSearchRequest request) {
            return List.of();
        }
    }

    private static final class RecordingTasks implements IndexSyncTaskRepository {
        private final List<IndexSyncTask> values = new ArrayList<>();

        @Override
        public IndexSyncTask save(IndexSyncTask task) {
            IndexSyncTask saved = new IndexSyncTask(
                    (long) values.size() + 1, task.knowledgeId(), task.targetIndex(),
                    task.operation(), task.status(), task.targetStatus(),
                    task.retryCount(), task.errorMessage(), task.createdAt(),
                    task.startedAt(), task.finishedAt());
            values.add(saved);
            return saved;
        }

        @Override public void update(IndexSyncTask task) { }
        @Override public boolean claim(IndexSyncTask task, Instant startedAt) { return true; }
        @Override public List<IndexSyncTask> findRunnable(int retryLimit, int limit) {
            return values;
        }
        @Override public List<IndexSyncTask> findActiveByKnowledgeId(long knowledgeId) {
            return values.stream().filter(value -> value.knowledgeId() == knowledgeId).toList();
        }
    }
}
