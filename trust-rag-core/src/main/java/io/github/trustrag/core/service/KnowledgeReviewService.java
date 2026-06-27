package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewRequest;
import io.github.trustrag.core.model.ReviewStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewCallback;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.util.List;

/**
 * 人工终审服务。
 *
 * <p>中可信知识只有通过这里的审核并重新写入索引后，才会进入 HIGH_ENABLED。
 * 审核回调用于通知业务系统，例如刷新缓存或同步外部知识目录。</p>
 */
public final class KnowledgeReviewService {

    private static final System.Logger LOGGER = System.getLogger(KnowledgeReviewService.class.getName());

    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeLineageRepository lineageRepository;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeIndexService indexService;
    private final List<ReviewCallback> callbacks;
    private final TransactionRunner transactionRunner;
    private final KnowledgeStateMachine stateMachine;
    private final Clock clock;

    public KnowledgeReviewService(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            List<ReviewCallback> callbacks,
            TransactionRunner transactionRunner,
            KnowledgeStateMachine stateMachine,
            Clock clock) {
        this(
                knowledgeRepository, reviewTaskRepository, lineageRepository, embeddingClient,
                new VectorOnlyKnowledgeIndexService(vectorStore), callbacks, transactionRunner,
                stateMachine, clock);
    }

    public KnowledgeReviewService(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            List<ReviewCallback> callbacks,
            TransactionRunner transactionRunner,
            KnowledgeStateMachine stateMachine,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.lineageRepository = lineageRepository;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
        this.callbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
        this.transactionRunner = transactionRunner;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    public List<KnowledgeItem> findCandidates(KnowledgeStatus status, int limit, int offset) {
        return findCandidates(status, null, null, limit, offset);
    }

    public List<KnowledgeItem> findCandidates(
            KnowledgeStatus status,
            io.github.trustrag.core.model.TrustLevel trustLevel,
            io.github.trustrag.core.model.ScopeType scopeType,
            int limit,
            int offset) {
        return knowledgeRepository.findCandidates(
                status == null ? KnowledgeStatus.HUMAN_REVIEW_PENDING : status,
                trustLevel,
                scopeType,
                Math.min(Math.max(limit, 1), 200),
                Math.max(offset, 0));
    }

    public KnowledgeItem approve(long knowledgeId, ReviewRequest request) {
        validateReviewRequest(request);
        KnowledgeItem candidate = loadReviewableCandidate(knowledgeId);
        ReviewTask task = loadPendingTask(knowledgeId);
        ScopeContext context = new ScopeContext(
                candidate.userId(), candidate.conversationId(), candidate.projectId(), candidate.tenantId());
        // 审核人可以修订标题和内容；最终 hash 必须基于修订后的正式内容重新计算。
        KnowledgeItem indexing = candidate
                .beginHighApproval(
                        request.reviewerId(), request.modifiedTitle(), request.modifiedContent(), clock.instant())
                .withHash(KnowledgeHashes.scopedHash(
                        request.modifiedContent() == null || request.modifiedContent().isBlank()
                                ? candidate.content()
                                : request.modifiedContent(),
                        candidate.scopeType() == io.github.trustrag.core.model.ScopeType.GLOBAL_CANDIDATE
                                ? io.github.trustrag.core.model.ScopeType.GLOBAL
                                : candidate.scopeType(),
                        context));

        knowledgeRepository.findByHash(indexing.hash())
                .filter(existing -> !existing.id().equals(candidate.id()))
                .ifPresent(existing -> {
                    throw new InvalidKnowledgeStateException(
                            "Approved content duplicates knowledge item " + existing.id());
                });
        stateMachine.validate(candidate.status(), KnowledgeStatus.INDEXING);
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    indexing, candidate.status(), candidate.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge item " + knowledgeId + " was changed by another reviewer");
            }
            lineageRepository.save(new KnowledgeLineage(
                    null, knowledgeId, candidate.id(), null, null,
                    "HIGH_APPROVAL_INDEXING", "HUMAN", request.reviewerId(), clock.instant()));
        });

        KnowledgeItem enabled;
        try {
            List<Float> vector = embeddingClient.embed(indexing.content());
            if (vector == null || vector.size() != embeddingClient.dimension()) {
                throw new TrustRagException("Embedding dimension mismatch while approving knowledge " + knowledgeId);
            }
            enabled = indexing.withIndexState(
                    KnowledgeStatus.HIGH_ENABLED,
                    Long.toString(indexing.id()),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            // 先完成索引写入，再提交 HIGH_ENABLED 状态，避免高可信知识无索引可召回。
            indexService.upsert(enabled, vector);
        } catch (Exception exception) {
            KnowledgeItem failed = indexing.withIndexState(
                    KnowledgeStatus.INDEX_FAILED,
                    indexing.embeddingId(),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            if (knowledgeRepository.updateIfState(
                    failed, KnowledgeStatus.INDEXING, indexing.version())) {
                lineageRepository.save(new KnowledgeLineage(
                        null, knowledgeId, candidate.id(), null, null,
                        "HIGH_APPROVAL_INDEX_FAILED", "SYSTEM", "trust-rag", clock.instant()));
            }
            throw new TrustRagException("Approval indexing failed for knowledge " + knowledgeId, exception);
        }
        KnowledgeItem approved = enabled;
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    approved, KnowledgeStatus.INDEXING, indexing.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while completing approval: " + knowledgeId);
            }
            reviewTaskRepository.update(task.complete(
                    ReviewStatus.APPROVED, request.reviewerId(), "approve",
                    request.comment(), clock.instant()));
            lineageRepository.save(new KnowledgeLineage(
                    null, approved.id(), candidate.id(), null, null,
                    "MEDIUM_TO_HIGH_APPROVED", "HUMAN", request.reviewerId(), clock.instant()));
        });
        notifyCallbacks(approved);
        return approved;
    }

    public KnowledgeItem reject(long knowledgeId, ReviewRequest request) {
        validateReviewRequest(request);
        KnowledgeItem candidate = loadReviewableCandidate(knowledgeId);
        ReviewTask task = loadPendingTask(knowledgeId);
        KnowledgeItem rejected = candidate.reject(request.comment(), clock.instant());
        stateMachine.validate(candidate.status(), KnowledgeStatus.REJECTED);
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(rejected, candidate.status(), candidate.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge item " + knowledgeId + " was changed by another reviewer");
            }
            reviewTaskRepository.update(task.complete(
                    ReviewStatus.REJECTED, request.reviewerId(), "reject", request.comment(), clock.instant()));
            lineageRepository.save(new KnowledgeLineage(
                    null, rejected.id(), candidate.id(), null, null,
                    "HUMAN_REVIEW_REJECTED", "HUMAN", request.reviewerId(), clock.instant()));
        });
        try {
            indexService.delete(knowledgeId);
        } catch (Exception exception) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Rejected knowledge has stale index data that is blocked by relational status: " + knowledgeId,
                    exception);
        }
        return rejected;
    }

    private KnowledgeItem loadReviewableCandidate(long knowledgeId) {
        KnowledgeItem candidate = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new KnowledgeNotFoundException(knowledgeId));
        if (!candidate.status().isHumanReviewable()) {
            throw new InvalidKnowledgeStateException(
                    "Knowledge item " + knowledgeId + " is not reviewable: " + candidate.status());
        }
        return candidate;
    }

    private ReviewTask loadPendingTask(long knowledgeId) {
        return reviewTaskRepository.findPendingByKnowledgeId(knowledgeId)
                .orElseThrow(() -> new InvalidKnowledgeStateException(
                        "No pending review task for knowledge " + knowledgeId));
    }

    private void validateReviewRequest(ReviewRequest request) {
        if (request == null || request.reviewerId() == null || request.reviewerId().isBlank()) {
            throw new InvalidKnowledgeStateException("reviewerId must not be blank");
        }
    }

    private void notifyCallbacks(KnowledgeItem item) {
        for (ReviewCallback callback : callbacks) {
            try {
                callback.onApproved(item);
            } catch (Exception exception) {
                // 回调失败不回滚审核结果，业务系统可基于日志或外部重试自行补偿。
                LOGGER.log(System.Logger.Level.WARNING, "Review callback failed for knowledge " + item.id(), exception);
            }
        }
    }
}
