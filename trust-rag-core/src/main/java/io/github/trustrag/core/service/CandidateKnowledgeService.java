package io.github.trustrag.core.service;

import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public final class CandidateKnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final KnowledgeVisibilityPolicy visibilityPolicy;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CandidateKnowledgeService(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public KnowledgeItem submit(CandidateKnowledge candidate, ScopeContext context) {
        if (candidate == null || candidate.content() == null || candidate.content().isBlank()) {
            throw new IllegalArgumentException("Candidate content must not be blank");
        }
        if (candidate.trustLevel() != TrustLevel.LOW || candidate.status() != KnowledgeStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("New candidates must be LOW and PENDING_REVIEW");
        }
        if (candidate.scopeType() == ScopeType.GLOBAL) {
            throw new IllegalArgumentException("Unreviewed candidates cannot use GLOBAL scope");
        }
        Instant now = clock.instant();
        String hash = KnowledgeHashes.scopedHash(candidate.content(), candidate.scopeType(), context);
        Optional<KnowledgeItem> existing = knowledgeRepository.findByHash(hash);
        if (existing.isPresent()) {
            return reusableExisting(existing.get());
        }

        KnowledgeItem item = new KnowledgeItem(
                null, candidate.title(), candidate.claim(), candidate.content(), null, "candidate",
                candidate.trustLevel(), candidate.status(), candidate.scopeType(),
                context.userId(), context.conversationId(), context.projectId(), context.tenantId(),
                candidate.sourceType(), candidate.sourceRef(), candidate.evidence(),
                null, null, null, candidate.confidence(), null, 1, hash,
                null, null, null, now, now, null);
        visibilityPolicy.validateOwnership(item);
        try {
            return transactionRunner.required(() -> {
                KnowledgeItem saved = knowledgeRepository.save(item);
                reviewTaskRepository.save(ReviewTask.pending(saved.id(), now));
                return saved;
            });
        } catch (RuntimeException exception) {
            return knowledgeRepository.findByHash(hash)
                    .map(this::reusableExisting)
                    .orElseThrow(() -> exception);
        }
    }

    private KnowledgeItem reusableExisting(KnowledgeItem existing) {
        return switch (existing.status()) {
            case PENDING_REVIEW, INDEX_FAILED, ENABLED -> existing;
            case INDEXING, REJECTED, EXPIRED -> throw new IllegalStateException(
                    "Duplicate knowledge exists in non-reusable state: " + existing.status());
        };
    }
}
