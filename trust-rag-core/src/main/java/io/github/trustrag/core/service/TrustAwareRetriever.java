package io.github.trustrag.core.service;

import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.exception.RetrievalException;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievalResult;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrustAwareRetriever {

    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeVisibilityPolicy visibilityPolicy;
    private final RetrievalOptions options;

    public TrustAwareRetriever(
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            RetrievalOptions options) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.knowledgeRepository = knowledgeRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.options = options;
    }

    public RetrievalResult retrieve(RagRequest request, ScopeContext scope, List<String> queries) {
        try {
            int finalTopK = request.topK() == null
                    ? options.highTrustTopK() + options.mediumTrustTopK() + options.lowTrustTopK()
                    : request.topK();
            double minScore = request.minScore() == null ? options.minVectorScore() : request.minScore();
            Map<Long, Double> scores = new LinkedHashMap<>();

            for (String query : queries) {
                List<Float> vector = embeddingClient.embed(query);
                validateDimension(vector);
                merge(scores, searchLayer(
                        vector, scope, Set.of(TrustLevel.HIGH),
                        Set.of(KnowledgeStatus.HIGH_ENABLED),
                        options.highTrustTopK(), minScore, false));
                if (options.mediumTrustTopK() > 0) {
                    merge(scores, searchLayer(
                            vector, scope, Set.of(TrustLevel.MEDIUM),
                            Set.of(KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING),
                            options.mediumTrustTopK(), minScore, false));
                }
                if (options.lowTrustTopK() > 0) {
                    merge(scores, searchLayer(
                            vector, scope, Set.of(TrustLevel.LOW),
                            Set.of(KnowledgeStatus.LOW_ENABLED),
                            options.lowTrustTopK(), minScore, options.allowGlobalLowCandidate()));
                }
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
        } catch (RetrievalException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RetrievalException("Trust-aware retrieval failed", exception);
        }
    }

    private List<VectorHit> searchLayer(
            List<Float> vector,
            ScopeContext scope,
            Set<TrustLevel> trustLevels,
            Set<KnowledgeStatus> statuses,
            int topK,
            double minScore,
            boolean allowGlobalCandidate) {
        if (topK == 0) {
            return List.of();
        }
        return vectorStore.search(new VectorSearchRequest(
                vector, scope, trustLevels, statuses, topK, minScore, allowGlobalCandidate));
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
}
