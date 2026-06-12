package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.KnowledgeImportRequest;
import io.github.trustrag.core.model.KnowledgeImportResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.spi.ChunkStrategy;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class KnowledgeIngestionService {

    private final ChunkStrategy chunkStrategy;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeVisibilityPolicy visibilityPolicy;
    private final ReviewTaskRepository reviewTaskRepository;
    private final TransactionRunner transactionRunner;
    private final LifecycleOptions lifecycleOptions;
    private final Clock clock;

    public KnowledgeIngestionService(
            ChunkStrategy chunkStrategy,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            ReviewTaskRepository reviewTaskRepository,
            TransactionRunner transactionRunner,
            LifecycleOptions lifecycleOptions,
            Clock clock) {
        this.chunkStrategy = chunkStrategy;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.knowledgeRepository = knowledgeRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.reviewTaskRepository = reviewTaskRepository;
        this.transactionRunner = transactionRunner;
        this.lifecycleOptions = lifecycleOptions;
        this.clock = clock;
    }

    public KnowledgeImportResult importKnowledge(KnowledgeImportRequest request) {
        validate(request);
        ScopeContext context = new ScopeContext(
                request.userId(), request.conversationId(), request.projectId(), request.tenantId());
        TrustLevel trustLevel = defaultTrust(request.trustLevel());
        ScopeType scopeType = defaultScope(request.scopeType());
        if (trustLevel == TrustLevel.LOW && scopeType == ScopeType.GLOBAL) {
            throw new InvalidRagRequestException("Low-trust knowledge cannot use GLOBAL scope");
        }
        List<String> chunks = chunkStrategy.split(request.content());
        List<Long> ids = new ArrayList<>();
        int duplicates = 0;
        int failures = 0;

        for (int index = 0; index < chunks.size(); index++) {
            String content = chunks.get(index);
            String hash = KnowledgeHashes.scopedHash(content, scopeType, context);
            if (knowledgeRepository.findByHash(hash).isPresent()) {
                duplicates++;
                continue;
            }
            Instant now = clock.instant();
            String title = chunks.size() == 1 ? request.title() : request.title() + " #" + (index + 1);
            KnowledgeItem item = new KnowledgeItem(
                    null, title, null, content, null, "document_chunk",
                    trustLevel, KnowledgeStatus.INDEXING, scopeType,
                    request.userId(), request.conversationId(), request.projectId(), request.tenantId(),
                    request.sourceType(), request.sourceRef(), request.sourceRef(),
                    null, null, null, 1.0, 0.0, 1, hash,
                    null, null, null, now, now, expiry(trustLevel, now));
            visibilityPolicy.validateOwnership(item);

            KnowledgeItem saved;
            try {
                saved = knowledgeRepository.save(item);
                index(saved);
                ids.add(saved.id());
            } catch (Exception exception) {
                failures++;
            }
        }
        return new KnowledgeImportResult(ids.size(), duplicates, failures, ids);
    }

    public KnowledgeItem retryIndex(long knowledgeId) {
        KnowledgeItem item = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new KnowledgeNotFoundException(knowledgeId));
        if (item.status() != KnowledgeStatus.INDEX_FAILED && item.status() != KnowledgeStatus.INDEXING) {
            throw new TrustRagException("Knowledge item is not retryable: " + item.status());
        }
        return index(item);
    }

    private KnowledgeItem index(KnowledgeItem item) {
        try {
            List<Float> vector = embeddingClient.embed(item.content());
            if (vector == null || vector.size() != embeddingClient.dimension()) {
                throw new TrustRagException("Embedding dimension mismatch while indexing knowledge " + item.id());
            }
            KnowledgeItem enabled = item.withIndexState(
                    enabledStatus(item.trustLevel()),
                    Long.toString(item.id()),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            vectorStore.upsert(enabled, vector);
            transactionRunner.required(() -> {
                knowledgeRepository.update(enabled);
                if (enabled.status() == KnowledgeStatus.HUMAN_REVIEW_PENDING
                        && reviewTaskRepository.findPendingByKnowledgeId(enabled.id()).isEmpty()) {
                    reviewTaskRepository.save(ReviewTask.pending(enabled.id(), clock.instant()));
                }
            });
            return enabled;
        } catch (Exception exception) {
            KnowledgeItem failed = item.withIndexState(
                    KnowledgeStatus.INDEX_FAILED,
                    item.embeddingId(),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            knowledgeRepository.update(failed);
            throw new TrustRagException("Knowledge indexing failed for id " + item.id(), exception);
        }
    }

    private void validate(KnowledgeImportRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new InvalidRagRequestException("content must not be blank");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new InvalidRagRequestException("title must not be blank");
        }
    }

    private TrustLevel defaultTrust(TrustLevel value) {
        return value == null ? TrustLevel.HIGH : value;
    }

    private ScopeType defaultScope(ScopeType value) {
        return value == null ? ScopeType.GLOBAL : value;
    }

    private KnowledgeStatus enabledStatus(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case HIGH -> KnowledgeStatus.HIGH_ENABLED;
            case MEDIUM -> KnowledgeStatus.HUMAN_REVIEW_PENDING;
            case LOW -> KnowledgeStatus.LOW_ENABLED;
        };
    }

    private Instant expiry(TrustLevel trustLevel, Instant now) {
        return switch (trustLevel) {
            case HIGH -> null;
            case MEDIUM -> now.plus(lifecycleOptions.mediumTtlDays(), ChronoUnit.DAYS);
            case LOW -> now.plus(lifecycleOptions.lowTtlDays(), ChronoUnit.DAYS);
        };
    }
}
