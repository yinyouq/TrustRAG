package io.github.trustrag.core.service;

import io.github.trustrag.core.config.ConflictDetectionOptions;
import io.github.trustrag.core.model.ConflictRecord;
import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConflictDetectorTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void recordsConflictAgainstHighTrustKnowledge() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        KnowledgeItem candidate = repository.save(item(
                1L, TrustLevel.LOW, KnowledgeStatus.PROMOTION_RUNNING,
                ScopeType.GLOBAL_CANDIDATE, "The feature is disabled."));
        KnowledgeItem existing = repository.save(item(
                2L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED,
                ScopeType.GLOBAL, "The feature is enabled."));
        RecordingConflictRepository conflicts = new RecordingConflictRepository();
        DefaultConflictDetector detector = new DefaultConflictDetector(
                repository,
                vectorStore(existing.id()),
                conflicts,
                (left, right) -> new io.github.trustrag.core.spi.KnowledgeRelationJudge.RelationJudgement(
                        ConflictType.CONFLICT, 0.90, "Claims contradict"),
                new ConflictDetectionOptions(true, true, false, 5, 0.75, false),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = detector.check(candidate, List.of(1.0f));

        assertThat(result.conflictRisk()).isEqualTo(0.90);
        assertThat(result.records()).hasSize(1);
        assertThat(conflicts.values)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.candidateKnowledgeId()).isEqualTo(candidate.id());
                    assertThat(record.existingKnowledgeId()).isEqualTo(existing.id());
                    assertThat(record.conflictType()).isEqualTo(ConflictType.CONFLICT);
                });
    }

    private KnowledgeVectorStore vectorStore(long existingId) {
        return new KnowledgeVectorStore() {
            @Override
            public void initialize() {
            }

            @Override
            public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            }

            @Override
            public void delete(long knowledgeId) {
            }

            @Override
            public List<VectorHit> search(VectorSearchRequest request) {
                return request.trustLevels().contains(TrustLevel.HIGH)
                        ? List.of(new VectorHit(existingId, 0.95))
                        : List.of();
            }
        };
    }

    private KnowledgeItem item(
            long id,
            TrustLevel trustLevel,
            KnowledgeStatus status,
            ScopeType scopeType,
            String content) {
        return new KnowledgeItem(
                id, "title-" + id, content, content, null, "test",
                trustLevel, status, scopeType, null, null, null, null,
                "official_doc", "source-" + id, "evidence",
                Long.toString(id), "test", 1, 1.0, 0.0, 1, "hash-" + id,
                null, null, null, NOW, NOW, null);
    }

    private static final class RecordingConflictRepository implements ConflictRecordRepository {
        private final List<ConflictRecord> values = new ArrayList<>();

        @Override
        public ConflictRecord save(ConflictRecord record) {
            ConflictRecord saved = new ConflictRecord(
                    (long) values.size() + 1,
                    record.candidateKnowledgeId(),
                    record.existingKnowledgeId(),
                    record.conflictType(),
                    record.confidence(),
                    record.reason(),
                    record.createdAt());
            values.add(saved);
            return saved;
        }

        @Override
        public List<ConflictRecord> findByCandidateKnowledgeId(long knowledgeId) {
            return values.stream()
                    .filter(value -> value.candidateKnowledgeId() == knowledgeId)
                    .toList();
        }

        @Override
        public List<ConflictRecord> find(int limit, int offset) {
            return values.stream().skip(offset).limit(limit).toList();
        }
    }
}
