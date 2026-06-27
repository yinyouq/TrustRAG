package io.github.trustrag.core.service;

import io.github.trustrag.core.config.PromotionOptions;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.model.ConflictCheckResult;
import io.github.trustrag.core.model.DuplicateCheckResult;
import io.github.trustrag.core.model.EvidenceVerificationResult;
import io.github.trustrag.core.model.KnowledgeGovernance;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.model.PrivacyResult;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
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

/**
 * 验证 DefaultKnowledgePromotionEngine 的关键行为、边界条件和回归场景。
 */
class DefaultKnowledgePromotionEngineTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void promotesQualifiedLowKnowledgeToHumanReviewAtDefaultThreshold() {
        Fixture fixture = fixture(PrivacyResult::allowed);
        KnowledgeItem candidate = fixture.repository.save(candidate(1L));

        var result = fixture.engine.evaluate(candidate.id());

        assertThat(result.action()).isEqualTo(PromotionAction.PROMOTE_TO_MEDIUM);
        assertThat(result.targetStatus()).isEqualTo(KnowledgeStatus.MEDIUM_ENABLED);
        assertThat(result.promotionScore()).isGreaterThanOrEqualTo(0.75);

        fixture.engine.promoteToMedium(candidate.id(), result);

        KnowledgeItem promoted = fixture.repository.findById(candidate.id()).orElseThrow();
        assertThat(promoted.trustLevel()).isEqualTo(TrustLevel.MEDIUM);
        assertThat(promoted.status()).isEqualTo(KnowledgeStatus.HUMAN_REVIEW_PENDING);
        assertThat(fixture.reviewRepository.pending).isNotNull();
        assertThat(fixture.vectorStore.upserted).isEqualTo(promoted.id());
        assertThat(fixture.lineageRepository.values)
                .extracting(KnowledgeLineage::action)
                .containsExactly(
                        "PROMOTION_STARTED",
                        "LOW_TO_MEDIUM_PROMOTED",
                        "HUMAN_REVIEW_QUEUED");
    }

    @Test
    void privacyRiskBlocksOtherwiseQualifiedCandidate() {
        Fixture fixture = fixture(content -> new PrivacyResult(
                false, "[REDACTED]", 0.90, "Sensitive content"));
        KnowledgeItem candidate = fixture.repository.save(candidate(2L));

        var result = fixture.engine.evaluate(candidate.id());

        assertThat(result.action()).isEqualTo(PromotionAction.REJECT);
        assertThat(result.privacyRisk()).isEqualTo(0.90);
    }

    @Test
    void persistedPrivacyRiskSurvivesSanitizationAndBlocksPromotion() {
        Fixture fixture = fixture(PrivacyResult::allowed);
        KnowledgeItem candidate = fixture.repository.save(candidate(3L, 0.90));

        var result = fixture.engine.evaluate(candidate.id());

        assertThat(result.action()).isEqualTo(PromotionAction.REJECT);
        assertThat(result.privacyRisk()).isEqualTo(0.90);
    }

    private Fixture fixture(io.github.trustrag.core.spi.PrivacyFilter privacyFilter) {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        TestReviewRepository reviewRepository = new TestReviewRepository();
        TestLineageRepository lineageRepository = new TestLineageRepository();
        TestVectorStore vectorStore = new TestVectorStore();
        DefaultKnowledgePromotionEngine engine = new DefaultKnowledgePromotionEngine(
                repository,
                reviewRepository,
                lineageRepository,
                embeddingClient(),
                vectorStore,
                privacyFilter,
                (candidate, vector) -> DuplicateCheckResult.none(),
                (candidate, similar) -> new PreReviewResult(
                        0.95, 0.90, 0.90, 0.05,
                        PromotionAction.PROMOTE_TO_MEDIUM,
                        "passed", candidate.claim(), List.of("test"), "{}"),
                candidate -> new EvidenceVerificationResult(0.95, 0.90, true, "verified"),
                (candidate, vector) -> ConflictCheckResult.none(),
                new PromotionOptions(true, 50, 3, 0.75, 0.60, 0.50, 0.30, 0.30, true),
                new LifecycleOptions(30, 180, true, 3, 3),
                new KnowledgeStateMachine(),
                TransactionRunner.direct(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(repository, reviewRepository, lineageRepository, vectorStore, engine);
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

    private KnowledgeItem candidate(long id) {
        return candidate(id, 0.0);
    }

    private KnowledgeItem candidate(long id, double privacyRisk) {
        KnowledgeGovernance governance = new KnowledgeGovernance(
                null, null, null, null, null, null, null, null, null, null, null,
                privacyRisk, 0.0, 0.0, null, null, null, null, null,
                5, 1, 0, List.of(), null, null);
        return new KnowledgeItem(
                id, "candidate", "verified claim", "verified content",
                null, "candidate", TrustLevel.LOW, KnowledgeStatus.LOW_PENDING,
                ScopeType.GLOBAL_CANDIDATE, null, null, null, null,
                "official", "https://example.invalid/source",
                "Evidence independently confirms this claim and its applicability.",
                null, null, null, 0.95, privacyRisk, 1, "hash-" + id,
                null, null, null, NOW, NOW, null, governance);
    }

    private record Fixture(
            TestKnowledgeRepository repository,
            TestReviewRepository reviewRepository,
            TestLineageRepository lineageRepository,
            TestVectorStore vectorStore,
            DefaultKnowledgePromotionEngine engine) {
    }

    private static final class TestReviewRepository implements ReviewTaskRepository {
        private ReviewTask pending;

        @Override
        public ReviewTask save(ReviewTask task) {
            pending = new ReviewTask(
                    1L, task.knowledgeId(), task.status(), task.reviewerId(),
                    task.reviewAction(), task.reviewComment(), task.createdAt(), task.reviewedAt());
            return pending;
        }

        @Override
        public void update(ReviewTask task) {
            pending = task;
        }

        @Override
        public Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId) {
            return Optional.ofNullable(pending);
        }
    }

    private static final class TestLineageRepository implements KnowledgeLineageRepository {
        private final List<KnowledgeLineage> values = new ArrayList<>();

        @Override
        public KnowledgeLineage save(KnowledgeLineage lineage) {
            values.add(lineage);
            return lineage;
        }

        @Override
        public List<KnowledgeLineage> findByKnowledgeId(long knowledgeId) {
            return values.stream().filter(value -> value.knowledgeId() == knowledgeId).toList();
        }
    }

    private static final class TestVectorStore implements KnowledgeVectorStore {
        private Long upserted;

        @Override
        public void initialize() {
        }

        @Override
        public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            upserted = knowledge.id();
        }

        @Override
        public void delete(long knowledgeId) {
        }

        @Override
        public List<VectorHit> search(VectorSearchRequest request) {
            return List.of();
        }
    }
}
