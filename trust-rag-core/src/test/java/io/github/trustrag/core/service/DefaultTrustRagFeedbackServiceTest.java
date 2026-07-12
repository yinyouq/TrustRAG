package io.github.trustrag.core.service;

import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.FeedbackRepository;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.RagTraceRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTrustRagFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void correctionUsesGeneratedQuestionAsCandidateTitle() {
        TestKnowledgeRepository knowledgeRepository = new TestKnowledgeRepository();
        CandidateKnowledgeService candidateService = new CandidateKnowledgeService(
                knowledgeRepository,
                new RecordingTaskRepository(),
                new RecordingLineageRepository(),
                new KnowledgeVisibilityPolicy(),
                new LifecycleOptions(30, 90, true, 3, 3),
                TransactionRunner.direct(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        DefaultTrustRagFeedbackService service = new DefaultTrustRagFeedbackService(
                new RecordingFeedbackRepository(),
                new FixedTraceRepository(
                        "trace-1",
                        "What does the equipment list include?"),
                knowledgeRepository,
                new DefaultPrivacyFilter(true, true, true),
                new DefaultScopeClassifier(),
                new DefaultCandidateExtractor(),
                candidateService,
                TransactionRunner.direct(),
                new CorrectionTitleGenerator(prompt -> new LlmResponse(
                        "What weapons, armor, and accessories are included?",
                        null,
                        TokenUsage.unknown())));

        KnowledgeItem candidate = service.submitFeedback(new RagFeedbackRequest(
                        "trace-1",
                        FeedbackType.CORRECTION,
                        "The original answer missed armor details.",
                        "Weapons include fourteen melee/ranged weapons. Armor includes head, chest, hand, waist, and leg parts.",
                        "user-1",
                        "conversation-1",
                        null,
                        null))
                .orElseThrow();

        assertThat(candidate.title()).isEqualTo("What weapons, armor, and accessories are included?");
        assertThat(candidate.content()).contains("Weapons include fourteen melee/ranged weapons.");
        assertThat(candidate.status()).isEqualTo(KnowledgeStatus.LOW_PENDING);
        assertThat(candidate.trustLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(candidate.scopeType()).isEqualTo(ScopeType.GLOBAL_CANDIDATE);
    }

    private static final class FixedTraceRepository implements RagTraceRepository {
        private final String traceId;
        private final String question;

        private FixedTraceRepository(String traceId, String question) {
            this.traceId = traceId;
            this.question = question;
        }

        @Override
        public void save(RagTrace trace) {
        }

        @Override
        public boolean existsByTraceId(String traceId) {
            return this.traceId.equals(traceId);
        }

        @Override
        public Optional<String> findQuestionByTraceId(String traceId) {
            return existsByTraceId(traceId) ? Optional.of(question) : Optional.empty();
        }

        @Override
        public List<Long> findUsedKnowledgeIds(String traceId) {
            return List.of();
        }
    }

    private static final class RecordingFeedbackRepository implements FeedbackRepository {
        private final List<RagFeedbackRequest> values = new ArrayList<>();

        @Override
        public long save(RagFeedbackRequest request) {
            values.add(request);
            return values.size();
        }
    }

    private static final class RecordingTaskRepository implements PromotionTaskRepository {
        private final List<PromotionTask> values = new ArrayList<>();

        @Override
        public PromotionTask save(PromotionTask task) {
            PromotionTask saved = new PromotionTask(
                    (long) values.size() + 1,
                    task.knowledgeId(),
                    task.status(),
                    task.taskType(),
                    task.retryCount(),
                    task.errorMessage(),
                    task.startedAt(),
                    task.finishedAt(),
                    task.createdAt());
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
