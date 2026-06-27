package io.github.trustrag.core.service;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 候选知识沉淀服务。
 *
 * <p>用户纠错或知识缺口生成的内容先进入 LOW_PENDING，并创建晋升任务。
 * 后续自动治理通过后，才可能进入中可信和人工终审链路。</p>
 */
public final class CandidateKnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final PromotionTaskRepository promotionTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final KnowledgeVisibilityPolicy visibilityPolicy;
    private final LifecycleOptions lifecycleOptions;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CandidateKnowledgeService(
            KnowledgeRepository knowledgeRepository,
            PromotionTaskRepository promotionTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            LifecycleOptions lifecycleOptions,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.promotionTaskRepository = promotionTaskRepository;
        this.lineageRepository = lineageRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.lifecycleOptions = lifecycleOptions;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public KnowledgeItem submit(CandidateKnowledge candidate, ScopeContext context) {
        if (candidate == null || candidate.content() == null || candidate.content().isBlank()) {
            throw new IllegalArgumentException("Candidate content must not be blank");
        }
        if (candidate.trustLevel() != TrustLevel.LOW || candidate.status() != KnowledgeStatus.LOW_PENDING) {
            throw new IllegalArgumentException("New candidates must be LOW and LOW_PENDING");
        }
        if (candidate.scopeType() == ScopeType.GLOBAL) {
            throw new IllegalArgumentException("Unreviewed candidates cannot use GLOBAL scope");
        }
        Instant now = clock.instant();
        String hash = KnowledgeHashes.scopedHash(candidate.content(), candidate.scopeType(), context);
        Optional<KnowledgeItem> existing = knowledgeRepository.findByHash(hash);
        if (existing.isPresent()) {
            // 同作用域重复候选不再新建记录，只增加复用次数，避免低可信池膨胀。
            return reuseAndIncrement(existing.get());
        }

        var governance = io.github.trustrag.core.model.KnowledgeGovernance.empty()
                .withPrivacyRisk(candidate.privacyRisk());
        KnowledgeItem item = new KnowledgeItem(
                null, candidate.title(), candidate.claim(), candidate.content(), null, "candidate",
                candidate.trustLevel(), candidate.status(), candidate.scopeType(),
                context.userId(), context.conversationId(), context.projectId(), context.tenantId(),
                candidate.sourceType(), candidate.sourceRef(), candidate.evidence(),
                null, null, null, candidate.confidence(), candidate.privacyRisk(), 1, hash,
                null, null, null, now, now,
                now.plus(lifecycleOptions.lowTtlDays(), ChronoUnit.DAYS), governance);
        visibilityPolicy.validateOwnership(item);
        try {
            return transactionRunner.required(() -> {
                KnowledgeItem saved = knowledgeRepository.save(item);
                // 候选创建后立即排入 LOW_TO_MEDIUM 晋升队列，由晋升引擎统一做治理检查。
                promotionTaskRepository.save(PromotionTask.pending(
                        saved.id(), PromotionTaskType.LOW_TO_MEDIUM, now));
                lineageRepository.save(new KnowledgeLineage(
                        null, saved.id(), null,
                        "user_correction".equals(candidate.sourceType()) ? candidate.sourceRef() : null,
                        null, "LOW_CANDIDATE_CREATED", "SYSTEM", "trust-rag", now));
                lineageRepository.save(KnowledgeLineage.system(
                        saved.id(), "PROMOTION_QUEUED", now));
                return saved;
            });
        } catch (RuntimeException exception) {
            return knowledgeRepository.findByHash(hash)
                    .map(this::reuseAndIncrement)
                    .orElseThrow(() -> exception);
        }
    }

    private KnowledgeItem reuseAndIncrement(KnowledgeItem existing) {
        KnowledgeItem reusable = reusableExisting(existing);
        knowledgeRepository.incrementUsageCount(reusable.id());
        return reusable;
    }

    private KnowledgeItem reusableExisting(KnowledgeItem existing) {
        return switch (existing.status()) {
            case LOW_PENDING, LOW_ENABLED, PROMOTION_PENDING, PROMOTION_RUNNING,
                    MEDIUM_ENABLED, HUMAN_REVIEW_PENDING, HIGH_ENABLED, INDEX_FAILED -> existing;
            case INDEXING, REJECTED, CONFLICT, EXPIRED, MERGE_PENDING, ROLLBACK ->
                    throw new IllegalStateException(
                    "Duplicate knowledge exists in non-reusable state: " + existing.status());
        };
    }
}
