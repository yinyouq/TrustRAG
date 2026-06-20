package io.github.trustrag.core.service;

import io.github.trustrag.core.config.PromotionOptions;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.ConflictCheckResult;
import io.github.trustrag.core.model.DuplicateAction;
import io.github.trustrag.core.model.DuplicateCheckResult;
import io.github.trustrag.core.model.EvidenceVerificationResult;
import io.github.trustrag.core.model.KnowledgeGovernance;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.model.PrivacyResult;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.model.PromotionResult;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.ConflictDetector;
import io.github.trustrag.core.spi.DuplicateDetector;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.EvidenceVerifier;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgePromotionEngine;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.LlmPreReviewer;
import io.github.trustrag.core.spi.PrivacyFilter;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.ClaimNormalizer;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;

public final class DefaultKnowledgePromotionEngine implements KnowledgePromotionEngine {

    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeIndexService indexService;
    private final PrivacyFilter privacyFilter;
    private final DuplicateDetector duplicateDetector;
    private final LlmPreReviewer preReviewer;
    private final EvidenceVerifier evidenceVerifier;
    private final ConflictDetector conflictDetector;
    private final PromotionOptions options;
    private final LifecycleOptions lifecycleOptions;
    private final KnowledgeStateMachine stateMachine;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public DefaultKnowledgePromotionEngine(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            PrivacyFilter privacyFilter,
            DuplicateDetector duplicateDetector,
            LlmPreReviewer preReviewer,
            EvidenceVerifier evidenceVerifier,
            ConflictDetector conflictDetector,
            PromotionOptions options,
            LifecycleOptions lifecycleOptions,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        this(
                knowledgeRepository, reviewTaskRepository, lineageRepository, embeddingClient,
                new VectorOnlyKnowledgeIndexService(vectorStore), privacyFilter, duplicateDetector,
                preReviewer, evidenceVerifier, conflictDetector, options, lifecycleOptions,
                stateMachine, transactionRunner, clock);
    }

    public DefaultKnowledgePromotionEngine(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            PrivacyFilter privacyFilter,
            DuplicateDetector duplicateDetector,
            LlmPreReviewer preReviewer,
            EvidenceVerifier evidenceVerifier,
            ConflictDetector conflictDetector,
            PromotionOptions options,
            LifecycleOptions lifecycleOptions,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.lineageRepository = lineageRepository;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
        this.privacyFilter = privacyFilter;
        this.duplicateDetector = duplicateDetector;
        this.preReviewer = preReviewer;
        this.evidenceVerifier = evidenceVerifier;
        this.conflictDetector = conflictDetector;
        this.options = options;
        this.lifecycleOptions = lifecycleOptions;
        this.stateMachine = stateMachine;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public PromotionResult evaluate(long knowledgeId) {
        KnowledgeItem candidate = load(knowledgeId);
        if (!candidate.status().isPromotionCandidate()
                && candidate.status() != KnowledgeStatus.PROMOTION_RUNNING) {
            throw new InvalidKnowledgeStateException(
                    "Knowledge item is not eligible for promotion: " + candidate.status());
        }
        KnowledgeItem running = candidate;
        if (candidate.status() != KnowledgeStatus.PROMOTION_RUNNING) {
            stateMachine.validate(candidate.status(), KnowledgeStatus.PROMOTION_RUNNING);
            running = candidate.withStatus(KnowledgeStatus.PROMOTION_RUNNING, clock.instant());
            KnowledgeItem started = running;
            transactionRunner.required(() -> {
                if (!knowledgeRepository.updateIfState(
                        started, candidate.status(), candidate.version())) {
                    throw new InvalidKnowledgeStateException(
                            "Knowledge item was changed before promotion started: " + knowledgeId);
                }
                lineageRepository.save(KnowledgeLineage.system(
                        knowledgeId, "PROMOTION_STARTED", clock.instant()));
            });
        }

        String effectiveClaim = hasText(running.claim()) ? running.claim() : running.content();
        String normalizedClaim = ClaimNormalizer.normalize(effectiveClaim);
        KnowledgeGovernance governance = running.governance()
                .withClaim(normalizedClaim, ClaimNormalizer.hash(normalizedClaim));
        running = running.withGovernance(governance, clock.instant());
        knowledgeRepository.update(running);

        List<Float> vector = embeddingClient.embed(running.content());
        validateVector(vector, knowledgeId);

        DuplicateCheckResult duplicate = duplicateDetector.check(running, vector);
        if (duplicate.action() == DuplicateAction.REUSE_EXISTING) {
            knowledgeRepository.incrementUsageCount(duplicate.existingKnowledgeId());
            return result(
                    running, PromotionAction.REJECT, false, TrustLevel.LOW,
                    KnowledgeStatus.REJECTED, 0.0, zeroPreReview(running),
                    new EvidenceVerificationResult(0.0, 0.0, false, duplicate.reason()),
                    0.0, 0.0, 0.0, duplicate.reason());
        }
        if (duplicate.action() == DuplicateAction.MERGE_PENDING) {
            return result(
                    running, PromotionAction.MERGE_PENDING, false, TrustLevel.LOW,
                    KnowledgeStatus.MERGE_PENDING, 0.0, zeroPreReview(running),
                    new EvidenceVerificationResult(0.0, 0.0, false, duplicate.reason()),
                    0.0, 0.0, 0.0, duplicate.reason());
        }

        PrivacyResult privacy = privacyFilter.filter(running.content());
        double persistedPrivacyRisk = Math.max(
                running.privacyScore() == null ? 0.0 : running.privacyScore(),
                running.governance().privacyRisk() == null
                        ? 0.0
                        : running.governance().privacyRisk());
        double detectedPrivacyRisk = privacy.allowed()
                ? 0.0
                : Math.max(privacy.riskScore(), 0.80);
        double privacyRisk = Math.max(persistedPrivacyRisk, detectedPrivacyRisk);
        ConflictCheckResult conflict = conflictDetector.check(running, vector);
        List<KnowledgeItem> similarKnowledge = similarKnowledge(conflict);
        PreReviewResult preReview = options.llmPreReviewEnabled()
                ? preReviewer.review(running, similarKnowledge)
                : zeroPreReview(running);
        EvidenceVerificationResult evidence = evidenceVerifier.verify(running);
        double staleRisk = staleRisk(running, clock.instant());
        double feedbackScore = feedbackScore(running);
        double usageScore = Math.min(1.0, running.governance().usageCount() / 5.0);
        double promotionScore = clamp(
                0.25 * preReview.qualityScore()
                        + 0.25 * evidence.sourceScore()
                        + 0.20 * feedbackScore
                        + 0.15 * usageScore
                        + 0.10 * evidence.evidenceScore()
                        - 0.20 * conflict.conflictRisk()
                        - 0.20 * privacyRisk
                        - 0.10 * staleRisk);

        KnowledgeGovernance evaluated = running.governance()
                .withPreReview(preReview)
                .withClaim(preReview.normalizedClaim(), ClaimNormalizer.hash(preReview.normalizedClaim()))
                .withEvaluation(
                        "EVALUATED", promotionScore, evidence.sourceScore(), evidence.evidenceScore(),
                        feedbackScore, usageScore, privacyRisk, conflict.conflictRisk(),
                        staleRisk, clock.instant());
        knowledgeRepository.update(running.withGovernance(evaluated, clock.instant()));

        if (privacyRisk > options.maxPrivacyRisk()) {
            return result(
                    running, PromotionAction.REJECT, false, TrustLevel.LOW, KnowledgeStatus.REJECTED,
                    promotionScore, preReview, evidence, feedbackScore, usageScore,
                    conflict.conflictRisk(), privacyRisk, staleRisk,
                    "Privacy risk exceeds promotion limit");
        }
        if (staleRisk >= 0.80) {
            return result(
                    running, PromotionAction.MARK_EXPIRED, false, TrustLevel.LOW, KnowledgeStatus.EXPIRED,
                    promotionScore, preReview, evidence, feedbackScore, usageScore,
                    conflict.conflictRisk(), privacyRisk, staleRisk, "Knowledge is expired");
        }
        if (conflict.conflictRisk() > options.maxConflictRisk()) {
            return result(
                    running, PromotionAction.MARK_CONFLICT, false, TrustLevel.LOW, KnowledgeStatus.CONFLICT,
                    promotionScore, preReview, evidence, feedbackScore, usageScore,
                    conflict.conflictRisk(), privacyRisk, staleRisk,
                    "Conflict risk exceeds promotion limit");
        }
        if (preReview.suggestedAction() == PromotionAction.REJECT) {
            return result(
                    running, PromotionAction.REJECT, false, TrustLevel.LOW,
                    KnowledgeStatus.REJECTED, promotionScore, preReview, evidence,
                    feedbackScore, usageScore, conflict.conflictRisk(), privacyRisk,
                    staleRisk, "LLM pre-review rejected the candidate: " + preReview.reason());
        }
        if (preReview.suggestedAction() == PromotionAction.MARK_CONFLICT) {
            return result(
                    running, PromotionAction.MARK_CONFLICT, false, TrustLevel.LOW,
                    KnowledgeStatus.CONFLICT, promotionScore, preReview, evidence,
                    feedbackScore, usageScore,
                    Math.max(conflict.conflictRisk(), preReview.riskScore()),
                    privacyRisk, staleRisk,
                    "LLM pre-review found a conflict: " + preReview.reason());
        }
        if (preReview.suggestedAction() == PromotionAction.MARK_EXPIRED) {
            return result(
                    running, PromotionAction.MARK_EXPIRED, false, TrustLevel.LOW,
                    KnowledgeStatus.EXPIRED, promotionScore, preReview, evidence,
                    feedbackScore, usageScore, conflict.conflictRisk(), privacyRisk,
                    staleRisk, "LLM pre-review marked the candidate expired");
        }
        if (preReview.suggestedAction() == PromotionAction.MERGE_PENDING) {
            return result(
                    running, PromotionAction.MERGE_PENDING, false, TrustLevel.LOW,
                    KnowledgeStatus.MERGE_PENDING, promotionScore, preReview, evidence,
                    feedbackScore, usageScore, conflict.conflictRisk(), privacyRisk,
                    staleRisk, "LLM pre-review requires a merge");
        }
        boolean promotable = promotionScore >= options.minPromotionScore()
                && evidence.sourceScore() >= options.minSourceScore()
                && evidence.evidenceScore() >= options.minEvidenceScore()
                && evidence.sufficient();
        if (promotable && preReview.suggestedAction() == PromotionAction.PROMOTE_TO_MEDIUM) {
            return result(
                    running, PromotionAction.PROMOTE_TO_MEDIUM, true, TrustLevel.MEDIUM,
                    KnowledgeStatus.MEDIUM_ENABLED, promotionScore, preReview, evidence,
                    feedbackScore, usageScore, conflict.conflictRisk(), privacyRisk, staleRisk,
                    "Automatic checks passed; human final review is required");
        }
        return result(
                running, PromotionAction.KEEP_LOW, false, TrustLevel.LOW, KnowledgeStatus.LOW_ENABLED,
                promotionScore, preReview, evidence, feedbackScore, usageScore,
                conflict.conflictRisk(), privacyRisk, staleRisk,
                "Promotion thresholds were not met");
    }

    @Override
    public void promoteToMedium(long knowledgeId, PromotionResult result) {
        KnowledgeItem running = requireRunning(knowledgeId);
        KnowledgeGovernance governance = applyResult(running.governance(), result, "HUMAN_REVIEW_PENDING");
        KnowledgeItem medium = running
                .promoteToMedium(governance, null, governance.normalizedClaim(), clock.instant())
                .withExpiresAt(
                        clock.instant().plus(
                                lifecycleOptions.mediumTtlDays(), ChronoUnit.DAYS),
                        clock.instant())
                .withHash(KnowledgeHashes.scopedHash(
                        running.content(),
                        running.scopeType() == ScopeType.GLOBAL_CANDIDATE
                                ? ScopeType.GLOBAL
                                : running.scopeType(),
                        scope(running)));
        stateMachine.validate(KnowledgeStatus.PROMOTION_RUNNING, KnowledgeStatus.MEDIUM_ENABLED);
        KnowledgeItem pendingReview = medium.requestHumanReview(clock.instant());
        stateMachine.validate(KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING);
        List<Float> vector = embeddingClient.embed(pendingReview.content());
        validateVector(vector, knowledgeId);
        try {
            indexService.upsert(pendingReview, vector);
        } catch (RuntimeException exception) {
            markPromotionIndexFailed(
                    running, pendingReview,
                    "LOW_TO_MEDIUM_INDEX_FAILED");
            throw exception;
        }
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    medium, KnowledgeStatus.PROMOTION_RUNNING, running.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while being promoted: " + knowledgeId);
            }
            lineageRepository.save(KnowledgeLineage.system(
                    knowledgeId, "LOW_TO_MEDIUM_PROMOTED", clock.instant()));
            if (!knowledgeRepository.updateIfState(
                    pendingReview, KnowledgeStatus.MEDIUM_ENABLED, medium.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while entering human review: " + knowledgeId);
            }
            reviewTaskRepository.save(ReviewTask.pending(knowledgeId, clock.instant()));
            lineageRepository.save(KnowledgeLineage.system(
                    knowledgeId, "HUMAN_REVIEW_QUEUED", clock.instant()));
        });
    }

    @Override
    public void reject(long knowledgeId, String reason) {
        transition(knowledgeId, KnowledgeStatus.REJECTED, reason, "PROMOTION_REJECTED");
    }

    @Override
    public void markConflict(long knowledgeId, String reason) {
        transition(knowledgeId, KnowledgeStatus.CONFLICT, reason, "PROMOTION_CONFLICT");
    }

    @Override
    public void markExpired(long knowledgeId, String reason) {
        transition(knowledgeId, KnowledgeStatus.EXPIRED, reason, "PROMOTION_EXPIRED");
    }

    @Override
    public void markMergePending(long knowledgeId, String reason) {
        transition(knowledgeId, KnowledgeStatus.MERGE_PENDING, reason, "PROMOTION_MERGE_PENDING");
    }

    @Override
    public void keepLow(long knowledgeId, String reason) {
        KnowledgeItem running = requireRunning(knowledgeId);
        KnowledgeItem low = running.transition(
                TrustLevel.LOW, KnowledgeStatus.LOW_ENABLED, reason, clock.instant());
        List<Float> vector = embeddingClient.embed(low.content());
        validateVector(vector, knowledgeId);
        try {
            indexService.upsert(low, vector);
        } catch (RuntimeException exception) {
            markPromotionIndexFailed(running, low, "KEEP_LOW_INDEX_FAILED");
            throw exception;
        }
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    low, KnowledgeStatus.PROMOTION_RUNNING, running.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while keeping low: " + knowledgeId);
            }
            lineageRepository.save(KnowledgeLineage.system(
                    knowledgeId, "PROMOTION_KEPT_LOW", clock.instant()));
        });
    }

    private void transition(
            long knowledgeId,
            KnowledgeStatus target,
            String reason,
            String lineageAction) {
        KnowledgeItem running = requireRunning(knowledgeId);
        stateMachine.validate(running.status(), target);
        KnowledgeItem changed = running.transition(running.trustLevel(), target, reason, clock.instant());
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    changed, KnowledgeStatus.PROMOTION_RUNNING, running.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed during promotion transition: " + knowledgeId);
            }
            lineageRepository.save(KnowledgeLineage.system(
                    knowledgeId, lineageAction, clock.instant()));
        });
        if (!target.isRetrievable()) {
            try {
                indexService.delete(knowledgeId);
            } catch (Exception ignored) {
                // Relational status is authoritative and blocks stale vectors.
            }
        }
    }

    private PromotionResult result(
            KnowledgeItem item,
            PromotionAction action,
            boolean promotable,
            TrustLevel targetTrust,
            KnowledgeStatus targetStatus,
            double promotionScore,
            PreReviewResult preReview,
            EvidenceVerificationResult evidence,
            double feedbackScore,
            double usageScore,
            double conflictRisk,
            double privacyRisk,
            double staleRisk,
            String reason) {
        return new PromotionResult(
                item.id(), action, promotable, targetTrust, targetStatus, promotionScore,
                preReview.qualityScore(), evidence.sourceScore(), evidence.evidenceScore(),
                feedbackScore, usageScore, conflictRisk, privacyRisk, staleRisk, reason);
    }

    private void markPromotionIndexFailed(
            KnowledgeItem running,
            KnowledgeItem target,
            String action) {
        KnowledgeItem failed = target.withIndexState(
                        KnowledgeStatus.INDEX_FAILED,
                        target.embeddingId(),
                        embeddingClient.modelName(),
                        embeddingClient.dimension(),
                        clock.instant())
                .withGovernance(
                        target.governance().withStage(
                                "INDEX_RETRY_TARGET_" + target.status().name()),
                        clock.instant());
        if (knowledgeRepository.updateIfState(
                failed, KnowledgeStatus.PROMOTION_RUNNING, running.version())) {
            lineageRepository.save(KnowledgeLineage.system(
                    running.id(), action, clock.instant()));
        }
    }

    private PromotionResult result(
            KnowledgeItem item,
            PromotionAction action,
            boolean promotable,
            TrustLevel targetTrust,
            KnowledgeStatus targetStatus,
            double promotionScore,
            PreReviewResult preReview,
            EvidenceVerificationResult evidence,
            double conflictRisk,
            double privacyRisk,
            double staleRisk,
            String reason) {
        return result(
                item, action, promotable, targetTrust, targetStatus, promotionScore,
                preReview, evidence, feedbackScore(item), usageScore(item),
                conflictRisk, privacyRisk, staleRisk, reason);
    }

    private KnowledgeGovernance applyResult(
            KnowledgeGovernance governance,
            PromotionResult result,
            String stage) {
        return governance.withEvaluation(
                stage, result.promotionScore(), result.sourceScore(), result.evidenceScore(),
                result.feedbackScore(), result.usageScore(), result.privacyRisk(),
                result.conflictRisk(), result.staleRisk(), clock.instant());
    }

    private PreReviewResult zeroPreReview(KnowledgeItem candidate) {
        String claim = hasText(candidate.claim()) ? candidate.claim() : candidate.content();
        return new PreReviewResult(
                0.0, 0.0, 0.0, 0.0, PromotionAction.KEEP_LOW,
                "LLM pre-review disabled", claim, List.of(), "{}");
    }

    private KnowledgeItem requireRunning(long knowledgeId) {
        KnowledgeItem item = load(knowledgeId);
        if (item.status() != KnowledgeStatus.PROMOTION_RUNNING) {
            throw new InvalidKnowledgeStateException(
                    "Knowledge is not in PROMOTION_RUNNING: " + item.status());
        }
        return item;
    }

    private KnowledgeItem load(long knowledgeId) {
        return knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new KnowledgeNotFoundException(knowledgeId));
    }

    private ScopeContext scope(KnowledgeItem item) {
        return new ScopeContext(
                item.userId(), item.conversationId(), item.projectId(), item.tenantId());
    }

    private double feedbackScore(KnowledgeItem item) {
        int positive = item.governance().positiveFeedbackCount();
        int negative = item.governance().negativeFeedbackCount();
        int total = positive + negative;
        return total == 0 ? 0.50 : clamp((double) positive / total);
    }

    private double usageScore(KnowledgeItem item) {
        return Math.min(1.0, item.governance().usageCount() / 5.0);
    }

    private double staleRisk(KnowledgeItem item, Instant now) {
        Instant validTo = item.governance().validTo();
        if (validTo != null && validTo.isBefore(now)) {
            return 1.0;
        }
        Instant expiresAt = item.expiresAt();
        return expiresAt != null && expiresAt.isBefore(now) ? 1.0 : 0.0;
    }

    private void validateVector(List<Float> vector, long knowledgeId) {
        if (vector == null || vector.size() != embeddingClient.dimension()) {
            throw new TrustRagException(
                    "Embedding dimension mismatch for knowledge " + knowledgeId);
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<KnowledgeItem> similarKnowledge(ConflictCheckResult conflict) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        conflict.records().forEach(record -> ids.add(record.existingKnowledgeId()));
        return knowledgeRepository.findAllByIds(ids);
    }
}
