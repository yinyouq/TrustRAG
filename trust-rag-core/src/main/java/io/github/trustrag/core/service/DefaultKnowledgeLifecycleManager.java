package io.github.trustrag.core.service;

import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

public final class DefaultKnowledgeLifecycleManager implements KnowledgeLifecycleManager {

    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeStateMachine stateMachine;
    private final LifecycleOptions options;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public DefaultKnowledgeLifecycleManager(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeStateMachine stateMachine,
            LifecycleOptions options,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.lineageRepository = lineageRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.stateMachine = stateMachine;
        this.options = options;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public int expireDueKnowledge() {
        if (!options.autoExpireEnabled()) {
            return 0;
        }
        int expired = 0;
        for (KnowledgeItem item : knowledgeRepository.findExpired(clock.instant(), 500)) {
            stateMachine.validate(item.status(), KnowledgeStatus.EXPIRED);
            KnowledgeItem changed = item.transition(
                    item.trustLevel(), KnowledgeStatus.EXPIRED, "validity period ended", clock.instant());
            if (knowledgeRepository.updateIfState(changed, item.status(), item.version())) {
                lineageRepository.save(KnowledgeLineage.system(
                        item.id(), "KNOWLEDGE_EXPIRED", clock.instant()));
                deleteVector(item.id());
                expired++;
            }
        }
        return expired;
    }

    @Override
    public int downgradeNegativeFeedback() {
        int changed = 0;
        for (KnowledgeItem item : knowledgeRepository.findNegativeFeedbackCandidates(
                options.negativeFeedbackDowngradeThreshold(), 500)) {
            downgrade(item.id(), "trust-rag", "negative feedback threshold reached");
            changed++;
        }
        return changed;
    }

    @Override
    public int retryFailedIndexes() {
        if (options.indexFailedRetryLimit() == 0) {
            return 0;
        }
        int succeeded = 0;
        for (TrustLevel trustLevel : TrustLevel.values()) {
            for (KnowledgeItem item : knowledgeRepository.findByTrustAndStatuses(
                    trustLevel, Set.of(KnowledgeStatus.INDEX_FAILED),
                    options.indexFailedRetryLimit(), 0)) {
                if (reindex(item)) {
                    succeeded++;
                }
            }
        }
        return succeeded;
    }

    @Override
    public KnowledgeItem downgrade(long knowledgeId, String operatorId, String reason) {
        KnowledgeItem item = load(knowledgeId);
        TrustLevel targetTrust;
        KnowledgeStatus targetStatus;
        if (item.trustLevel() == TrustLevel.HIGH) {
            targetTrust = TrustLevel.MEDIUM;
            targetStatus = KnowledgeStatus.MEDIUM_ENABLED;
        } else if (item.trustLevel() == TrustLevel.MEDIUM) {
            targetTrust = TrustLevel.LOW;
            targetStatus = KnowledgeStatus.LOW_ENABLED;
        } else {
            throw new InvalidKnowledgeStateException("LOW knowledge cannot be downgraded");
        }
        stateMachine.validate(item.status(), targetStatus);
        Instant now = clock.instant();
        KnowledgeItem downgraded = item.transition(
                        targetTrust, targetStatus, reason, now)
                .withExpiresAt(expiry(targetTrust, now), now);
        upsert(downgraded);
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(downgraded, item.status(), item.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed during downgrade: " + knowledgeId);
            }
            lineageRepository.save(new KnowledgeLineage(
                    null, knowledgeId, item.id(), null, null,
                    "KNOWLEDGE_DOWNGRADED", "HUMAN", operatorId, clock.instant()));
        });
        return downgraded;
    }

    @Override
    public KnowledgeItem rollback(long knowledgeId, String operatorId, String reason) {
        KnowledgeItem item = load(knowledgeId);
        stateMachine.validate(item.status(), KnowledgeStatus.ROLLBACK);
        KnowledgeItem rollbackMarker = item.rollback(clock.instant());
        if (!knowledgeRepository.updateIfState(rollbackMarker, item.status(), item.version())) {
            throw new InvalidKnowledgeStateException(
                    "Knowledge changed during rollback: " + knowledgeId);
        }
        Instant now = clock.instant();
        KnowledgeItem restored = rollbackMarker.restorePrevious(now)
                .withExpiresAt(expiry(rollbackMarker.governance().previousTrustLevel(), now), now)
                .withGovernance(
                        rollbackMarker.governance().withPrevious(
                                item.trustLevel(), item.status()),
                        clock.instant());
        stateMachine.validate(KnowledgeStatus.ROLLBACK, restored.status());
        upsert(restored);
        transactionRunner.required(() -> {
            knowledgeRepository.update(restored);
            lineageRepository.save(new KnowledgeLineage(
                    null, knowledgeId, item.id(), null, null,
                    "KNOWLEDGE_ROLLED_BACK: " + reason, "HUMAN", operatorId, clock.instant()));
        });
        return restored;
    }

    @Override
    public KnowledgeItem merge(
            List<Long> sourceKnowledgeIds,
            long targetKnowledgeId,
            String operatorId) {
        if (sourceKnowledgeIds == null || sourceKnowledgeIds.isEmpty()) {
            throw new IllegalArgumentException("sourceKnowledgeIds must not be empty");
        }
        KnowledgeItem target = load(targetKnowledgeId);
        for (Long sourceId : sourceKnowledgeIds.stream().distinct().toList()) {
            if (sourceId == null || sourceId == targetKnowledgeId) {
                continue;
            }
            KnowledgeItem source = load(sourceId);
            KnowledgeItem rejected = source.reject(
                    "merged into knowledge " + targetKnowledgeId, clock.instant());
            if (knowledgeRepository.updateIfState(rejected, source.status(), source.version())) {
                knowledgeRepository.incrementUsageCount(targetKnowledgeId);
                lineageRepository.save(new KnowledgeLineage(
                        null, targetKnowledgeId, source.id(), null, null,
                        "KNOWLEDGE_MERGED", "HUMAN", operatorId, clock.instant()));
                deleteVector(source.id());
            }
        }
        return load(targetKnowledgeId);
    }

    private boolean reindex(KnowledgeItem item) {
        try {
            KnowledgeStatus target = switch (item.trustLevel()) {
                case HIGH -> KnowledgeStatus.HIGH_ENABLED;
                case MEDIUM -> KnowledgeStatus.HUMAN_REVIEW_PENDING;
                case LOW -> KnowledgeStatus.LOW_ENABLED;
            };
            KnowledgeItem enabled = item.withIndexState(
                    target, Long.toString(item.id()), embeddingClient.modelName(),
                    embeddingClient.dimension(), clock.instant());
            upsert(enabled);
            boolean updated = transactionRunner.required(() -> {
                if (!knowledgeRepository.updateIfState(
                        enabled, item.status(), item.version())) {
                    return false;
                }
                if (target == KnowledgeStatus.HUMAN_REVIEW_PENDING
                        && reviewTaskRepository.findPendingByKnowledgeId(item.id()).isEmpty()) {
                    reviewTaskRepository.save(ReviewTask.pending(item.id(), clock.instant()));
                }
                if (target == KnowledgeStatus.HIGH_ENABLED) {
                    reviewTaskRepository.findPendingByKnowledgeId(item.id())
                            .ifPresent(task -> reviewTaskRepository.update(task.complete(
                                    ReviewStatus.APPROVED,
                                    item.approvedBy() == null ? "trust-rag" : item.approvedBy(),
                                    "index-retry",
                                    "Approval index retry succeeded",
                                    clock.instant())));
                }
                return true;
            });
            if (updated) {
                lineageRepository.save(KnowledgeLineage.system(
                        item.id(), "INDEX_RETRY_SUCCEEDED", clock.instant()));
                return true;
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void upsert(KnowledgeItem item) {
        List<Float> vector = embeddingClient.embed(item.content());
        if (vector == null || vector.size() != embeddingClient.dimension()) {
            throw new IllegalStateException("Embedding dimension mismatch");
        }
        vectorStore.upsert(item, vector);
    }

    private KnowledgeItem load(long knowledgeId) {
        return knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new KnowledgeNotFoundException(knowledgeId));
    }

    private void deleteVector(long knowledgeId) {
        try {
            vectorStore.delete(knowledgeId);
        } catch (RuntimeException ignored) {
            // Relational status remains authoritative.
        }
    }

    private Instant expiry(TrustLevel trustLevel, Instant now) {
        return switch (trustLevel) {
            case HIGH -> null;
            case MEDIUM -> now.plus(options.mediumTtlDays(), ChronoUnit.DAYS);
            case LOW -> now.plus(options.lowTtlDays(), ChronoUnit.DAYS);
        };
    }
}
