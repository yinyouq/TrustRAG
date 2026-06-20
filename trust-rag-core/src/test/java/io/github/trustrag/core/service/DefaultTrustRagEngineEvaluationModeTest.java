package io.github.trustrag.core.service;

import io.github.trustrag.core.config.EngineOptions;
import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TraceType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.RagTraceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTrustRagEngineEvaluationModeTest {

    @Test
    void evaluationModeDoesNotMutateKnowledgeFlywheel() {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        TestKnowledgeRepository knowledgeRepository = new TestKnowledgeRepository();
        KnowledgeItem knowledge = knowledgeRepository.save(new KnowledgeItem(
                null, "TrustRAG", "claim", "TrustRAG evaluates retrieval quality.", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "eval-hash",
                null, null, null, now, now, null));
        CapturingTraceRepository traceRepository = new CapturingTraceRepository();
        AtomicInteger gapCalls = new AtomicInteger();
        TrustAwareRetriever retriever = new TrustAwareRetriever(
                new StaticEmbeddingClient(),
                new StaticVectorStore(knowledge.id()),
                knowledgeRepository,
                new KnowledgeVisibilityPolicy(),
                new RetrievalOptions(
                        1, 1, 0, 0, 0, 0, 0, 0, 0.0,
                        1.0, 0.8, 0.7, 0.6, 0.6, 0.6, 0.1, false));
        DefaultTrustRagEngine engine = new DefaultTrustRagEngine(
                request -> List.of(request.question()),
                retriever,
                new NoOpRerankClient(),
                new DefaultPromptBuilder(List.of()),
                prompt -> new LlmResponse("answer", 0.9, new TokenUsage(2, 3)),
                traceRepository,
                knowledgeRepository,
                trace -> {
                    gapCalls.incrementAndGet();
                    return new GapDetectionResult(true, 0.9, List.of(), "gap", true, false);
                },
                new DefaultScopeResolver(),
                new EngineOptions(true, false, true, true, 1, 1, false),
                Clock.fixed(now, ZoneOffset.UTC));

        RagAnswer answer = engine.ask(RagRequest.builder()
                .question("What is TrustRAG?")
                .evaluationMode(true)
                .evalRunId(7L)
                .evalCaseId(8L)
                .build());

        assertThat(answer.usedKnowledge()).singleElement()
                .extracting(item -> item.knowledgeId())
                .isEqualTo(knowledge.id());
        assertThat(knowledgeRepository.usageIncrements(knowledge.id())).isZero();
        assertThat(gapCalls).hasValue(0);
        assertThat(traceRepository.saved().traceType()).isEqualTo(TraceType.EVAL);
        assertThat(traceRepository.saved().evalRunId()).isEqualTo(7L);
        assertThat(traceRepository.saved().evalCaseId()).isEqualTo(8L);
    }

    private record StaticEmbeddingClient() implements EmbeddingClient {
        @Override
        public List<Float> embed(String text) {
            return List.of(1.0F);
        }

        @Override
        public String modelName() {
            return "test";
        }

        @Override
        public int dimension() {
            return 1;
        }
    }

    private record StaticVectorStore(long knowledgeId) implements KnowledgeVectorStore {
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
            return List.of(new VectorHit(knowledgeId, 0.95));
        }
    }

    private static final class CapturingTraceRepository implements RagTraceRepository {
        private RagTrace saved;

        @Override
        public void save(RagTrace trace) {
            this.saved = trace;
        }

        @Override
        public boolean existsByTraceId(String traceId) {
            return saved != null && saved.traceId().equals(traceId);
        }

        @Override
        public List<Long> findUsedKnowledgeIds(String traceId) {
            return saved == null ? List.of() : saved.usedChunks().stream()
                    .map(chunk -> chunk.knowledgeId())
                    .toList();
        }

        RagTrace saved() {
            return Optional.ofNullable(saved).orElseThrow();
        }
    }
}
