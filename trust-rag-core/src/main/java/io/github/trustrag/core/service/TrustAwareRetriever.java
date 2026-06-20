package io.github.trustrag.core.service;

import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.config.HybridRetrievalOptions;
import io.github.trustrag.core.exception.RetrievalException;
import io.github.trustrag.core.model.HybridCandidate;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievalResult;
import io.github.trustrag.core.model.RetrievalMode;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.SearchType;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.RrfFusionService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TrustAwareRetriever {

    private static final System.Logger LOGGER = System.getLogger(TrustAwareRetriever.class.getName());
    private static final Set<TrustLevel> ALL_TRUST_LEVELS = Set.of(
            TrustLevel.HIGH, TrustLevel.MEDIUM, TrustLevel.LOW);
    private static final Set<KnowledgeStatus> RETRIEVABLE_STATUSES = Set.of(
            KnowledgeStatus.HIGH_ENABLED,
            KnowledgeStatus.MEDIUM_ENABLED,
            KnowledgeStatus.HUMAN_REVIEW_PENDING,
            KnowledgeStatus.LOW_ENABLED);

    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeKeywordStore keywordStore;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeVisibilityPolicy visibilityPolicy;
    private final RetrievalOptions options;
    private final HybridRetrievalOptions hybridOptions;
    private final RrfFusionService fusionService;
    private final Executor executor;

    public TrustAwareRetriever(
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            RetrievalOptions options) {
        this(
                embeddingClient, vectorStore, new NoOpKnowledgeKeywordStore(),
                knowledgeRepository, visibilityPolicy, options,
                HybridRetrievalOptions.vectorOnly(options.defaultTopK()),
                new DefaultRrfFusionService(), Runnable::run);
    }

    public TrustAwareRetriever(
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            RetrievalOptions options,
            HybridRetrievalOptions hybridOptions,
            RrfFusionService fusionService,
            Executor executor) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.keywordStore = keywordStore;
        this.knowledgeRepository = knowledgeRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.options = options;
        this.hybridOptions = hybridOptions;
        this.fusionService = fusionService;
        this.executor = executor;
    }

    public RetrievalResult retrieve(RagRequest request, ScopeContext scope, List<String> queries) {
        try {
            if (hybridOptions.mode() == RetrievalMode.HYBRID_RRF) {
                return retrieveHybrid(request, scope, queries);
            }
            return retrieveVectorOnly(request, scope, queries);
        } catch (RetrievalException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RetrievalException("Trust-aware retrieval failed", exception);
        }
    }

    private RetrievalResult retrieveHybrid(
            RagRequest request,
            ScopeContext scope,
            List<String> queries) {
        Map<Long, CandidateEnvelope> fused = new LinkedHashMap<>();
        for (String query : queries) {
            CompletableFuture<BranchResult<VectorHit>> vectorFuture =
                    CompletableFuture.supplyAsync(
                            () -> vectorSearch(query, scope, request), executor);
            CompletableFuture<BranchResult<KeywordHit>> keywordFuture =
                    CompletableFuture.supplyAsync(
                            () -> keywordSearch(query, scope), executor);
            BranchResult<VectorHit> vector = vectorFuture.join();
            BranchResult<KeywordHit> keyword = keywordFuture.join();
            if (!vector.success() && !keyword.success()) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "Both vector and keyword retrieval failed for query: " + query);
                continue;
            }
            SearchType searchType = searchType(vector.success(), keyword.success());
            List<HybridCandidate> candidates = fusionService.fuse(
                    vector.hits(), keyword.hits(), hybridOptions.rrfK());
            for (HybridCandidate candidate : candidates) {
                CandidateEnvelope value = new CandidateEnvelope(candidate, searchType);
                fused.merge(
                        candidate.knowledgeId(),
                        value,
                        (left, right) -> left.candidate().rrfScore()
                                >= right.candidate().rrfScore() ? left : right);
            }
        }
        if (fused.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        Map<Long, KnowledgeItem> items = new HashMap<>();
        for (KnowledgeItem item : knowledgeRepository.findAllByIds(fused.keySet())) {
            items.put(item.id(), item);
        }
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (CandidateEnvelope envelope : fused.values()) {
            HybridCandidate candidate = envelope.candidate();
            KnowledgeItem item = items.get(candidate.knowledgeId());
            if (item == null
                    || !item.status().isRetrievable()
                    || !visibilityPolicy.isVisible(
                    item, scope, options.allowGlobalLowCandidate())) {
                continue;
            }
            double trustBoost = trustBoost(item.trustLevel());
            double finalScore = candidate.rrfScore() * trustBoost;
            chunks.add(new RetrievedChunk(
                    item.id(), item.title(), item.content(), item.sourceRef(),
                    item.trustLevel(), item.scopeType(),
                    candidate.vectorScore() == null ? 0.0 : candidate.vectorScore(),
                    candidate.vectorRank(),
                    candidate.keywordScore(),
                    candidate.keywordRank(),
                    candidate.rrfScore(),
                    envelope.searchType(),
                    null,
                    trustBoost,
                    finalScore,
                    false));
        }
        chunks.sort(Comparator
                .comparingDouble(RetrievedChunk::finalScore)
                .reversed()
                .thenComparingLong(RetrievedChunk::knowledgeId));
        int limit = request.topK() == null
                ? hybridOptions.fusionTopN()
                : request.topK();
        return new RetrievalResult(chunks.stream().limit(limit).toList());
    }

    private BranchResult<VectorHit> vectorSearch(
            String query,
            ScopeContext scope,
            RagRequest request) {
        try {
            List<Float> vector = embeddingClient.embed(query);
            validateDimension(vector);
            double minScore = request.minScore() == null
                    ? options.minVectorScore()
                    : request.minScore();
            return BranchResult.success(vectorStore.search(new VectorSearchRequest(
                    vector, scope, ALL_TRUST_LEVELS, RETRIEVABLE_STATUSES,
                    Set.of(), hybridOptions.vectorTopK(), minScore,
                    options.allowGlobalLowCandidate())));
        } catch (Exception exception) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Vector retrieval failed; falling back to keyword results",
                    exception);
            return BranchResult.failed();
        }
    }

    private BranchResult<KeywordHit> keywordSearch(String query, ScopeContext scope) {
        if (!keywordStore.available()) {
            return BranchResult.failed();
        }
        try {
            return BranchResult.success(keywordStore.search(new KeywordSearchRequest(
                    query, scope, ALL_TRUST_LEVELS, RETRIEVABLE_STATUSES,
                    Set.of(), hybridOptions.keywordTopK(),
                    options.allowGlobalLowCandidate())));
        } catch (Exception exception) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Keyword retrieval failed; falling back to vector results",
                    exception);
            return BranchResult.failed();
        }
    }

    private SearchType searchType(boolean vectorSucceeded, boolean keywordSucceeded) {
        if (vectorSucceeded && keywordSucceeded) {
            return SearchType.HYBRID_RRF;
        }
        return vectorSucceeded ? SearchType.VECTOR_ONLY : SearchType.KEYWORD_ONLY;
    }

    private RetrievalResult retrieveVectorOnly(
            RagRequest request,
            ScopeContext scope,
            List<String> queries) {
            int finalTopK = request.topK() == null
                    ? options.defaultTopK()
                    : request.topK();
            double minScore = request.minScore() == null ? options.minVectorScore() : request.minScore();
            Map<Long, Double> scores = new LinkedHashMap<>();

            for (String query : queries) {
                List<Float> vector = embeddingClient.embed(query);
                validateDimension(vector);
                merge(scores, searchLayer(
                        vector, scope, Set.of(TrustLevel.HIGH),
                        Set.of(KnowledgeStatus.HIGH_ENABLED),
                        Set.of(), options.highTrustTopK(), minScore, false));
                if (options.mediumTrustTopK() > 0) {
                    merge(scores, searchLayer(
                            vector, scope, Set.of(TrustLevel.MEDIUM),
                            Set.of(KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING),
                            Set.of(), options.mediumTrustTopK(), minScore, false));
                }
                mergeLowScopes(scores, vector, scope, minScore);
            }

            Map<Long, KnowledgeItem> items = new HashMap<>();
            for (KnowledgeItem item : knowledgeRepository.findAllByIds(scores.keySet())) {
                items.put(item.id(), item);
            }

            List<RetrievedChunk> chunks = new ArrayList<>();
            for (Map.Entry<Long, Double> entry : scores.entrySet()) {
                KnowledgeItem item = items.get(entry.getKey());
                if (item == null
                        || !item.status().isRetrievable()
                        || !visibilityPolicy.isVisible(item, scope, options.allowGlobalLowCandidate())) {
                    continue;
                }
                double trustScore = trustScore(item);
                double finalScore = 0.75 * entry.getValue() + 0.25 * trustScore;
                chunks.add(new RetrievedChunk(
                        item.id(), item.title(), item.content(), item.sourceRef(), item.trustLevel(), item.scopeType(),
                        entry.getValue(), null, trustScore, finalScore, false));
            }

            chunks.sort(Comparator
                    .comparing((RetrievedChunk value) -> trustPriority(value.trustLevel()))
                    .thenComparing(RetrievedChunk::finalScore, Comparator.reverseOrder()));
            return new RetrievalResult(chunks.stream().limit(finalTopK).toList());
    }

    private List<VectorHit> searchLayer(
            List<Float> vector,
            ScopeContext scope,
            Set<TrustLevel> trustLevels,
            Set<KnowledgeStatus> statuses,
            Set<ScopeType> scopeTypes,
            int topK,
            double minScore,
            boolean allowGlobalCandidate) {
        if (topK == 0) {
            return List.of();
        }
        return vectorStore.search(new VectorSearchRequest(
                vector, scope, trustLevels, statuses, scopeTypes,
                topK, minScore, allowGlobalCandidate));
    }

    private void mergeLowScopes(
            Map<Long, Double> scores,
            List<Float> vector,
            ScopeContext scope,
            double minScore) {
        mergeLowScope(scores, vector, scope, ScopeType.CONVERSATION,
                options.lowConversationTopK(), scope.conversationId(), minScore);
        mergeLowScope(scores, vector, scope, ScopeType.USER,
                options.lowUserTopK(), scope.userId(), minScore);
        mergeLowScope(scores, vector, scope, ScopeType.PROJECT,
                options.lowProjectTopK(), scope.projectId(), minScore);
        mergeLowScope(scores, vector, scope, ScopeType.TENANT,
                options.lowTenantTopK(), scope.tenantId(), minScore);
        if (options.allowGlobalLowCandidate()) {
            merge(scores, searchLayer(
                    vector, scope, Set.of(TrustLevel.LOW),
                    Set.of(KnowledgeStatus.LOW_ENABLED),
                    Set.of(ScopeType.GLOBAL_CANDIDATE),
                    options.lowGlobalCandidateTopK(), minScore, true));
        }
    }

    private void mergeLowScope(
            Map<Long, Double> scores,
            List<Float> vector,
            ScopeContext scope,
            ScopeType scopeType,
            int topK,
            String ownerId,
            double minScore) {
        if (ownerId == null || ownerId.isBlank()) {
            return;
        }
        merge(scores, searchLayer(
                vector, scope, Set.of(TrustLevel.LOW),
                Set.of(KnowledgeStatus.LOW_ENABLED),
                Set.of(scopeType), topK, minScore, false));
    }

    private void merge(Map<Long, Double> scores, List<VectorHit> hits) {
        for (VectorHit hit : hits) {
            scores.merge(hit.knowledgeId(), hit.vectorScore(), Math::max);
        }
    }

    private void validateDimension(List<Float> vector) {
        if (vector == null || vector.size() != embeddingClient.dimension()) {
            throw new RetrievalException(
                    "Embedding dimension mismatch: expected " + embeddingClient.dimension()
                            + " but got " + (vector == null ? 0 : vector.size()),
                    null);
        }
    }

    private double trustScore(KnowledgeItem item) {
        if (item.trustLevel() == TrustLevel.HIGH) {
            return options.highTrustWeight();
        }
        if (item.trustLevel() == TrustLevel.MEDIUM) {
            return options.mediumTrustWeight();
        }
        return switch (item.scopeType()) {
            case CONVERSATION -> options.lowConversationWeight();
            case USER -> options.lowUserWeight();
            case PROJECT -> options.lowProjectWeight();
            case TENANT -> options.lowTenantWeight();
            case GLOBAL -> 0.0;
            case GLOBAL_CANDIDATE -> options.lowGlobalCandidateWeight();
        };
    }

    private int trustPriority(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private double trustBoost(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case HIGH -> hybridOptions.highTrustBoost();
            case MEDIUM -> hybridOptions.mediumTrustBoost();
            case LOW -> hybridOptions.lowTrustBoost();
        };
    }

    private record CandidateEnvelope(HybridCandidate candidate, SearchType searchType) {
    }

    private record BranchResult<T>(boolean success, List<T> hits) {
        private static <T> BranchResult<T> success(List<T> hits) {
            return new BranchResult<>(true, hits == null ? List.of() : List.copyOf(hits));
        }

        private static <T> BranchResult<T> failed() {
            return new BranchResult<>(false, List.of());
        }
    }
}
