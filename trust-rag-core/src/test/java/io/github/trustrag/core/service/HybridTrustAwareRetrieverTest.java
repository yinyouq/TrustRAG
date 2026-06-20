package io.github.trustrag.core.service;

import io.github.trustrag.core.config.HybridRetrievalOptions;
import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievalMode;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.SearchType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridTrustAwareRetrieverTest {

    @Test
    void fusesVectorAndKeywordResultsWithRanks() {
        TestKnowledgeRepository repository = repository();
        TrustAwareRetriever retriever = retriever(
                repository,
                vectorStore(List.of(new VectorHit(1L, 0.9), new VectorHit(3L, 0.8)), false),
                keywordStore(List.of(new KeywordHit(2L, 12.0), new KeywordHit(1L, 10.0)), false));

        var result = retrieve(retriever);

        assertThat(result.chunks()).extracting(value -> value.knowledgeId())
                .containsExactly(1L, 2L, 3L);
        assertThat(result.chunks().get(0).searchType())
                .isEqualTo(SearchType.HYBRID_RRF);
        assertThat(result.chunks().get(0).vectorRank()).isEqualTo(1);
        assertThat(result.chunks().get(0).keywordRank()).isEqualTo(2);
    }

    @Test
    void degradesToVectorWhenKeywordSearchFails() {
        TrustAwareRetriever retriever = retriever(
                repository(),
                vectorStore(List.of(new VectorHit(1L, 0.9)), false),
                keywordStore(List.of(), true));

        assertThat(retrieve(retriever).chunks())
                .singleElement()
                .extracting(value -> value.searchType())
                .isEqualTo(SearchType.VECTOR_ONLY);
    }

    @Test
    void degradesToKeywordWhenVectorSearchFails() {
        TrustAwareRetriever retriever = retriever(
                repository(),
                vectorStore(List.of(), true),
                keywordStore(List.of(new KeywordHit(2L, 9.0)), false));

        assertThat(retrieve(retriever).chunks())
                .singleElement()
                .extracting(value -> value.searchType())
                .isEqualTo(SearchType.KEYWORD_ONLY);
    }

    @Test
    void returnsEmptyContextWhenBothRetrieversFail() {
        TrustAwareRetriever retriever = retriever(
                repository(),
                vectorStore(List.of(), true),
                keywordStore(List.of(), true));

        assertThat(retrieve(retriever).chunks()).isEmpty();
    }

    private TrustAwareRetriever retriever(
            TestKnowledgeRepository repository,
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore) {
        return new TrustAwareRetriever(
                embeddingClient(), vectorStore, keywordStore, repository,
                new KnowledgeVisibilityPolicy(), retrievalOptions(),
                new HybridRetrievalOptions(
                        RetrievalMode.HYBRID_RRF, 30, 30, 30, 60,
                        1.0, 0.92, 0.80),
                new DefaultRrfFusionService(),
                Runnable::run);
    }

    private io.github.trustrag.core.model.RetrievalResult retrieve(
            TrustAwareRetriever retriever) {
        return retriever.retrieve(
                RagRequest.builder().question("question").userId("user-1").build(),
                new ScopeContext("user-1", null, null, null),
                List.of("question"));
    }

    private TestKnowledgeRepository repository() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        repository.save(item(1L, TrustLevel.HIGH, ScopeType.GLOBAL, null));
        repository.save(item(2L, TrustLevel.HIGH, ScopeType.GLOBAL, null));
        repository.save(item(3L, TrustLevel.LOW, ScopeType.USER, "user-1"));
        return repository;
    }

    private EmbeddingClient embeddingClient() {
        return new EmbeddingClient() {
            @Override
            public List<Float> embed(String text) {
                return List.of(1.0f);
            }

            @Override
            public String modelName() {
                return "test";
            }

            @Override
            public int dimension() {
                return 1;
            }
        };
    }

    private KnowledgeVectorStore vectorStore(List<VectorHit> hits, boolean fail) {
        return new KnowledgeVectorStore() {
            @Override public void initialize() { }
            @Override public void upsert(KnowledgeItem knowledge, List<Float> vector) { }
            @Override public void delete(long knowledgeId) { }

            @Override
            public List<VectorHit> search(VectorSearchRequest request) {
                if (fail) {
                    throw new IllegalStateException("vector unavailable");
                }
                return hits;
            }
        };
    }

    private KnowledgeKeywordStore keywordStore(List<KeywordHit> hits, boolean fail) {
        return new KnowledgeKeywordStore() {
            @Override public void initialize() { }
            @Override public void upsert(KnowledgeItem knowledge) { }
            @Override public void delete(long knowledgeId) { }

            @Override
            public List<KeywordHit> search(KeywordSearchRequest request) {
                if (fail) {
                    throw new IllegalStateException("keyword unavailable");
                }
                return hits;
            }
        };
    }

    private RetrievalOptions retrievalOptions() {
        return new RetrievalOptions(
                8, 5, 3, 2, 2, 2, 2, 1, 0.0,
                1.0, 0.70, 0.60, 0.45, 0.45, 0.35, 0.15, false);
    }

    private KnowledgeItem item(
            long id,
            TrustLevel trustLevel,
            ScopeType scopeType,
            String userId) {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        return new KnowledgeItem(
                id, "title-" + id, "claim-" + id, "content-" + id, null, "test",
                trustLevel,
                trustLevel == TrustLevel.HIGH
                        ? KnowledgeStatus.HIGH_ENABLED
                        : KnowledgeStatus.LOW_ENABLED,
                scopeType, userId, null, null, null,
                "official", "source", "evidence", Long.toString(id), "test", 1,
                1.0, 0.0, 1, "hash-" + id, null, null, null, now, now, null);
    }
}
