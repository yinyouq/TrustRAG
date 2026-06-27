package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
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
import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.spi.ChunkStrategy;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.core.util.KnowledgeHashes;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识导入服务，负责把外部内容切块、去重、入库并写入检索索引。
 *
 * <p>它是文档导入、人工录入和纠错回流进入知识池的统一入口。</p>
 */
public final class KnowledgeIngestionService {

    private final ChunkStrategy chunkStrategy;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeIndexService indexService;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeLineageRepository lineageRepository;
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
            KnowledgeLineageRepository lineageRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            ReviewTaskRepository reviewTaskRepository,
            TransactionRunner transactionRunner,
            LifecycleOptions lifecycleOptions,
            Clock clock) {
        this(
                chunkStrategy, embeddingClient, new VectorOnlyKnowledgeIndexService(vectorStore),
                knowledgeRepository, lineageRepository, visibilityPolicy, reviewTaskRepository,
                transactionRunner, lifecycleOptions, clock);
    }

    public KnowledgeIngestionService(
            ChunkStrategy chunkStrategy,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            KnowledgeRepository knowledgeRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            ReviewTaskRepository reviewTaskRepository,
            TransactionRunner transactionRunner,
            LifecycleOptions lifecycleOptions,
            Clock clock) {
        this.chunkStrategy = chunkStrategy;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
        this.knowledgeRepository = knowledgeRepository;
        this.lineageRepository = lineageRepository;
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
            // scopedHash 把内容和作用域一起纳入去重，避免不同租户/项目的同文档互相覆盖。
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
                    null, null, null, now, now, expiry(trustLevel, now),
                    io.github.trustrag.core.model.KnowledgeGovernance.empty(),
                    request.sourceMetadata().withChunkIndex(index));
            visibilityPolicy.validateOwnership(item);

            KnowledgeItem saved;
            try {
                saved = transactionRunner.required(() -> {
                    KnowledgeItem created = knowledgeRepository.save(item);
                    lineageRepository.save(KnowledgeLineage.system(
                            created.id(), "KNOWLEDGE_IMPORTED", clock.instant()));
                    return created;
                });
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
        KnowledgeItem enabled;
        try {
            List<Float> vector = embeddingClient.embed(item.content());
            if (vector == null || vector.size() != embeddingClient.dimension()) {
                throw new TrustRagException("Embedding dimension mismatch while indexing knowledge " + item.id());
            }
            // 先写外部索引，再推进关系库状态，保证可检索状态只出现在索引写入成功之后。
            enabled = item.withIndexState(
                    enabledStatus(item.trustLevel()),
                    Long.toString(item.id()),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            indexService.upsert(enabled, vector);
        } catch (Exception exception) {
            KnowledgeItem failed = item.withIndexState(
                    KnowledgeStatus.INDEX_FAILED,
                    item.embeddingId(),
                    embeddingClient.modelName(),
                    embeddingClient.dimension(),
                    clock.instant());
            if (knowledgeRepository.updateIfState(
                    failed, item.status(), item.version())) {
                lineageRepository.save(KnowledgeLineage.system(
                        item.id(), "KNOWLEDGE_INDEX_FAILED", clock.instant()));
            }
            throw new TrustRagException("Knowledge indexing failed for id " + item.id(), exception);
        }
        KnowledgeItem indexed = enabled;
        transactionRunner.required(() -> {
            if (!knowledgeRepository.updateIfState(
                    indexed, item.status(), item.version())) {
                throw new InvalidKnowledgeStateException(
                        "Knowledge changed while indexing: " + item.id());
            }
            if (indexed.status() == KnowledgeStatus.HUMAN_REVIEW_PENDING
                    && reviewTaskRepository.findPendingByKnowledgeId(indexed.id()).isEmpty()) {
                reviewTaskRepository.save(ReviewTask.pending(indexed.id(), clock.instant()));
            }
            lineageRepository.save(KnowledgeLineage.system(
                    indexed.id(), "KNOWLEDGE_INDEXED", clock.instant()));
        });
        return indexed;
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
