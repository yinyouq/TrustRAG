package io.github.trustrag.core.service;

import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustAwareRetrieverTest {

    @Test
    void retrievesThreePoolsInTrustOrderAndFiltersInvisibleLowCandidates() {
        TestKnowledgeRepository repository = new TestKnowledgeRepository();
        repository.save(item(1L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL, null));
        repository.save(item(2L, TrustLevel.MEDIUM, KnowledgeStatus.MEDIUM_ENABLED, ScopeType.GLOBAL, null));
        repository.save(item(3L, TrustLevel.LOW, KnowledgeStatus.LOW_ENABLED, ScopeType.USER, "user-1"));
        repository.save(item(
                4L, TrustLevel.LOW, KnowledgeStatus.LOW_ENABLED, ScopeType.GLOBAL_CANDIDATE, null));

        TrustAwareRetriever retriever = new TrustAwareRetriever(
                embeddingClient(),
                vectorStore(),
                repository,
                new KnowledgeVisibilityPolicy(),
                new RetrievalOptions(
                        1, 1, 2, 0.0,
                        1.0, 0.70, 0.60, 0.45, 0.45, 0.35, 0.20, false));

        var result = retriever.retrieve(
                RagRequest.builder().question("question").userId("user-1").build(),
                new ScopeContext("user-1", null, null, null),
                List.of("question"));

        assertThat(result.chunks())
                .extracting(chunk -> chunk.knowledgeId())
                .containsExactly(1L, 2L, 3L);
        assertThat(result.chunks())
                .extracting(chunk -> chunk.trustLevel())
                .containsExactly(TrustLevel.HIGH, TrustLevel.MEDIUM, TrustLevel.LOW);
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

    private KnowledgeVectorStore vectorStore() {
        return new KnowledgeVectorStore() {
            @Override
            public void initialize() {
            }

            @Override
            public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            }

            @Override
            public void delete(long knowledgeId) {
            }

            @Override
            public List<VectorHit> search(VectorSearchRequest request) {
                if (request.trustLevels().contains(TrustLevel.HIGH)) {
                    return List.of(new VectorHit(1L, 0.70));
                }
                if (request.trustLevels().contains(TrustLevel.MEDIUM)) {
                    return List.of(new VectorHit(2L, 0.95));
                }
                return List.of(new VectorHit(3L, 0.99), new VectorHit(4L, 0.98));
            }
        };
    }

    private KnowledgeItem item(
            long id,
            TrustLevel trustLevel,
            KnowledgeStatus status,
            ScopeType scopeType,
            String userId) {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        return new KnowledgeItem(
                id, "title-" + id, "claim-" + id, "content-" + id, null, "test",
                trustLevel, status, scopeType, userId, null, null, null,
                "official", "source", "evidence", Long.toString(id), "test", 1,
                1.0, 0.0, 1, "hash-" + id, null, null, null, now, now, null);
    }
}
