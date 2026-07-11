package io.github.trustrag.core.service;

import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 默认知识生命周期管理器。
 *
 * <p>负责过期、负反馈降级、索引失败重试、人工降级、回滚和合并。
 * 它把关系库状态视为权威来源，外部索引失败时通过补偿任务恢复一致性。</p>
 */
public final class DefaultKnowledgeLifecycleManager implements KnowledgeLifecycleManager {

    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final PromotionTaskRepository promotionTaskRepository;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeIndexService indexService;
    private final KnowledgeStateMachine stateMachine;
    private final LifecycleOptions options;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public DefaultKnowledgeLifecycleManager(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            PromotionTaskRepository promotionTaskRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeStateMachine stateMachine,
            LifecycleOptions options,
            TransactionRunner transactionRunner,
            Clock clock) {
        this(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                promotionTaskRepository, embeddingClient,
                new VectorOnlyKnowledgeIndexService(vectorStore), stateMachine,
                options, transactionRunner, clock);
    }

    public DefaultKnowledgeLifecycleManager(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            PromotionTaskRepository promotionTaskRepository,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            KnowledgeStateMachine stateMachine,
            LifecycleOptions options,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.lineageRepository = lineageRepository;
        this.promotionTaskRepository = promotionTaskRepository;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
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
        // 先为 INDEX_FAILED 知识补齐重试任务，再按任务表的重试次数和抢占语义执行。
        for (TrustLevel trustLevel : TrustLevel.values()) {
            for (KnowledgeItem item : knowledgeRepository.findByTrustAndStatuses(
                    trustLevel, Set.of(KnowledgeStatus.INDEX_FAILED), 500, 0)) {
                if (promotionTaskRepository.findActiveByKnowledgeId(
                        item.id(), PromotionTaskType.INDEX_RETRY).isEmpty()) {
                    promotionTaskRepository.save(PromotionTask.pending(
                            item.id(), PromotionTaskType.INDEX_RETRY, clock.instant()));
                }
            }
        }
        int succeeded = 0;
        for (PromotionTask task : promotionTaskRepository.findRunnableByType(
                PromotionTaskType.INDEX_RETRY, options.indexFailedRetryLimit(), 500)) {
            if (!promotionTaskRepository.claim(task, clock.instant())) {
                continue;
            }
            PromotionTask running = task.start(clock.instant());
            KnowledgeItem item = knowledgeRepository.findById(task.knowledgeId()).orElse(null);
            if (item == null || item.status() != KnowledgeStatus.INDEX_FAILED) {
                promotionTaskRepository.update(
                        running.skip("Knowledge no longer requires index retry", clock.instant()));
                continue;
            }
            if (reindex(item)) {
                promotionTaskRepository.update(running.succeed(clock.instant()));
                succeeded++;
            } else {
                promotionTaskRepository.update(
                        running.fail("Index retry failed", clock.instant()));
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
        stateMachine.validate(item.status(), KnowledgeStatus.INDEXING);
        Instant now = clock.instant();
        // 降级需要重新写索引，因为可信等级和可见范围都是检索过滤条件。
        KnowledgeItem indexing = item.transition(
                        targetTrust, KnowledgeStatus.INDEXING, reason, now)
                .withExpiresAt(expiry(targetTrust, now), now)
                .withGovernance(
                        item.governance()
                                .withPrevious(item.trustLevel(), item.status())
                                .withStage(indexRetryStage(targetStatus)),
                        now);
        if (!knowledgeRepository.updateIfState(indexing, item.status(), item.version())) {
            throw new InvalidKnowledgeStateException(
                    "Knowledge changed during downgrade: " + knowledgeId);
        }
        KnowledgeItem downgraded = indexing.withIndexState(
                targetStatus, Long.toString(indexing.id()), embeddingClient.modelName(),
                embeddingClient.dimension(), now)
                .withGovernance(indexing.governance().withStage(null), now);
        try {
            upsert(downgraded);
        } catch (RuntimeException exception) {
            markIndexFailed(indexing, "DOWNGRADE_INDEX_FAILED");
            throw exception;
        }
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    downgraded, KnowledgeStatus.INDEXING, indexing.version())) {
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
        // 先进入 ROLLBACK 标记态，再恢复 previousTrust/previousStatus，便于审计链路表达回滚动作。
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
        try {
            upsert(restored);
        } catch (RuntimeException exception) {
            KnowledgeItem failed = rollbackMarker.withIndexState(
                    KnowledgeStatus.INDEX_FAILED,
                    rollbackMarker.embeddingId(), embeddingClient.modelName(),
                    embeddingClient.dimension(), clock.instant());
            knowledgeRepository.updateIfState(
                    failed, KnowledgeStatus.ROLLBACK, rollbackMarker.version());
            lineageRepository.save(KnowledgeLineage.system(
                    knowledgeId, "ROLLBACK_INDEX_FAILED", clock.instant()));
            throw exception;
        }
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    restored, KnowledgeStatus.ROLLBACK, rollbackMarker.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while completing rollback: " + knowledgeId);
            }
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
                // 被合并知识不再可检索，目标知识的复用次数增加，便于后续晋升评分。
                knowledgeRepository.incrementUsageCount(targetKnowledgeId);
                lineageRepository.save(new KnowledgeLineage(
                        null, targetKnowledgeId, source.id(), null, null,
                        "KNOWLEDGE_MERGED", "HUMAN", operatorId, clock.instant()));
                deleteVector(source.id());
            }
        }
        return load(targetKnowledgeId);
    }

    @Override
    public KnowledgeItem delete(long knowledgeId, String operatorId, String reason) {
        KnowledgeItem item = load(knowledgeId);
        stateMachine.validate(item.status(), KnowledgeStatus.REJECTED);
        KnowledgeItem deleted = item.reject(reason, clock.instant());
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(deleted, item.status(), item.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed during delete: " + knowledgeId);
            }
            lineageRepository.save(new KnowledgeLineage(
                    null, knowledgeId, item.id(), null, null,
                    "KNOWLEDGE_DELETED", "HUMAN", operatorId, clock.instant()));
        });
        deleteVector(knowledgeId);
        return deleted;
    }

    private boolean reindex(KnowledgeItem item) {
        try {
            KnowledgeStatus target = indexRetryTarget(item);
            KnowledgeItem enabled = item.withIndexState(
                    target, Long.toString(item.id()), embeddingClient.modelName(),
                    embeddingClient.dimension(), clock.instant())
                    .withGovernance(item.governance().withStage(null), clock.instant());
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
        indexService.upsert(item, vector);
    }

    private void markIndexFailed(KnowledgeItem indexing, String action) {
        KnowledgeItem failed = indexing.withIndexState(
                KnowledgeStatus.INDEX_FAILED,
                indexing.embeddingId(), embeddingClient.modelName(),
                embeddingClient.dimension(), clock.instant());
        knowledgeRepository.updateIfState(
                failed, KnowledgeStatus.INDEXING, indexing.version());
        lineageRepository.save(KnowledgeLineage.system(
                indexing.id(), action, clock.instant()));
    }

    private KnowledgeStatus indexRetryTarget(KnowledgeItem item) {
        String stage = item.governance().promotionStage();
        if (stage != null && stage.startsWith("INDEX_RETRY_TARGET_")) {
            try {
                return KnowledgeStatus.valueOf(stage.substring("INDEX_RETRY_TARGET_".length()));
            } catch (IllegalArgumentException ignored) {
                // Fall back to trust and lineage metadata for older records.
            }
        }
        if (item.trustLevel() == TrustLevel.HIGH) {
            return KnowledgeStatus.HIGH_ENABLED;
        }
        if (item.trustLevel() == TrustLevel.LOW) {
            return KnowledgeStatus.LOW_ENABLED;
        }
        KnowledgeStatus previous = item.governance().previousStatus();
        if (previous == KnowledgeStatus.MEDIUM_ENABLED
                || previous == KnowledgeStatus.HUMAN_REVIEW_PENDING) {
            return previous;
        }
        return KnowledgeStatus.HUMAN_REVIEW_PENDING;
    }

    private String indexRetryStage(KnowledgeStatus targetStatus) {
        return "INDEX_RETRY_TARGET_" + targetStatus.name();
    }

    private KnowledgeItem load(long knowledgeId) {
        return knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new KnowledgeNotFoundException(knowledgeId));
    }

    private void deleteVector(long knowledgeId) {
        try {
            indexService.delete(knowledgeId);
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
