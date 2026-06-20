package io.github.trustrag.core.service;

import io.github.trustrag.core.config.EngineOptions;
import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.exception.LlmInvocationException;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.RetrievalResult;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.UsedKnowledge;
import io.github.trustrag.core.spi.KnowledgeGapDetector;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.LlmClient;
import io.github.trustrag.core.spi.PromptBuilder;
import io.github.trustrag.core.spi.QueryRewriteService;
import io.github.trustrag.core.spi.RagTraceRepository;
import io.github.trustrag.core.spi.RerankClient;
import io.github.trustrag.core.spi.ScopeResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultTrustRagEngine implements TrustRagEngine {

    private static final System.Logger LOGGER = System.getLogger(DefaultTrustRagEngine.class.getName());

    private final QueryRewriteService queryRewriteService;
    private final TrustAwareRetriever retriever;
    private final RerankClient rerankClient;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final RagTraceRepository traceRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeGapDetector gapDetector;
    private final ScopeResolver scopeResolver;
    private final EngineOptions options;
    private final Clock clock;

    public DefaultTrustRagEngine(
            QueryRewriteService queryRewriteService,
            TrustAwareRetriever retriever,
            RerankClient rerankClient,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            RagTraceRepository traceRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeGapDetector gapDetector,
            ScopeResolver scopeResolver,
            EngineOptions options,
            Clock clock) {
        this.queryRewriteService = queryRewriteService;
        this.retriever = retriever;
        this.rerankClient = rerankClient;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.traceRepository = traceRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.gapDetector = gapDetector;
        this.scopeResolver = scopeResolver;
        this.options = options;
        this.clock = clock;
    }

    @Override
    public RagAnswer ask(RagRequest request) {
        validate(request);
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        RagTrace trace = RagTrace.start(request, startedAt);
        boolean evaluationMode = request.isEvaluationMode();

        try {
            List<String> queries = enabled(request.enableQueryRewrite(), options.queryRewriteEnabled())
                    ? queryRewriteService.rewrite(request)
                    : List.of(request.question().trim());
            if (queries == null || queries.isEmpty()) {
                queries = List.of(request.question().trim());
            }
            trace.rewrittenQueries(queries);

            ScopeContext scope = scopeResolver.resolve(request);
            RetrievalResult retrieval = retriever.retrieve(request, scope, queries);
            trace.retrievedChunks(retrieval.chunks());

            List<RetrievedChunk> ranked = enabled(request.enableRerank(), options.rerankEnabled())
                    ? normalizeRerank(retrieval.chunks(), rerankClient.rerank(request.question(), retrieval.chunks()))
                    : retrieval.chunks();
            trace.rerankedChunks(ranked);

            List<RetrievedChunk> used = ranked.stream()
                    .limit(options.promptMaxChunks())
                    .map(RetrievedChunk::markUsedInPrompt)
                    .toList();
            trace.usedChunks(used);

            String prompt = promptBuilder.build(request, used);
            if (options.savePrompt()) {
                trace.finalPrompt(prompt);
            }

            LlmResponse rawResponse;
            try {
                rawResponse = llmClient.generate(prompt);
            } catch (Exception exception) {
                throw new LlmInvocationException("LLM generation failed", exception);
            }
            LlmResponse response = normalizeResponse(rawResponse, used);
            trace.complete(response, GapDetectionResult.noGap(), elapsed(startedNanos));

            GapDetectionResult gap = GapDetectionResult.noGap();
            if (!evaluationMode && enabled(request.enableGapDetection(), options.gapDetectionEnabled())) {
                try {
                    gap = gapDetector.detect(trace);
                    if (!enabled(
                            request.enableCandidateExtraction(),
                            options.candidateExtractionEnabled())
                            && gap.shouldExtractCandidate()) {
                        gap = new GapDetectionResult(
                                gap.hasGap(), gap.gapScore(), gap.gapTypes(), gap.reason(),
                                false, gap.shouldCreateReviewTask());
                    }
                } catch (Exception exception) {
                    LOGGER.log(System.Logger.Level.ERROR, "Knowledge gap detection failed for trace " + trace.traceId(), exception);
                }
            }
            trace.complete(response, gap, elapsed(startedNanos));
            safeRecord(trace);
            if (!evaluationMode) {
                recordUsage(used);
            }
            return toAnswer(trace);
        } catch (RuntimeException exception) {
            trace.fail(exception, elapsed(startedNanos));
            safeRecord(trace);
            throw exception;
        } catch (Exception exception) {
            trace.fail(exception, elapsed(startedNanos));
            safeRecord(trace);
            throw new TrustRagException("TrustRAG execution failed", exception);
        }
    }

    private List<RetrievedChunk> normalizeRerank(
            List<RetrievedChunk> original,
            List<RetrievedChunk> reranked) {
        if (reranked == null) {
            return original;
        }
        Map<Long, RetrievedChunk> originals = new HashMap<>();
        original.forEach(chunk -> originals.put(chunk.knowledgeId(), chunk));
        double maxRrfScore = original.stream()
                .mapToDouble(RetrievedChunk::rrfScore)
                .max()
                .orElse(0.0);
        return reranked.stream()
                .filter(candidate -> originals.containsKey(candidate.knowledgeId()))
                .map(candidate -> {
                    RetrievedChunk trusted = originals.get(candidate.knowledgeId());
                    double rerankScore = candidate.rerankScore() == null
                            ? candidate.finalScore()
                            : candidate.rerankScore();
                    double normalizedRrf = maxRrfScore <= 0.0
                            ? trusted.finalScore()
                            : trusted.rrfScore() / maxRrfScore;
                    double finalScore = 0.70 * rerankScore + 0.30 * normalizedRrf;
                    return trusted.withRerankScore(rerankScore, finalScore);
                })
                .sorted(Comparator.comparing(
                        RetrievedChunk::finalScore, Comparator.reverseOrder()))
                .toList();
    }

    private LlmResponse normalizeResponse(LlmResponse response, List<RetrievedChunk> used) {
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new LlmInvocationException("LLM returned an empty response", null);
        }
        Double confidence = response.confidence();
        if (confidence == null) {
            confidence = used.stream().mapToDouble(RetrievedChunk::finalScore).max().orElse(0.0);
        }
        TokenUsage usage = response.tokenUsage() == null ? TokenUsage.unknown() : response.tokenUsage();
        return new LlmResponse(response.text(), confidence, usage);
    }

    private RagAnswer toAnswer(RagTrace trace) {
        List<UsedKnowledge> usedKnowledge = trace.usedChunks().stream()
                .map(chunk -> new UsedKnowledge(
                        chunk.knowledgeId(), chunk.title(), chunk.content(), chunk.trustLevel(), chunk.scopeType(),
                        chunk.finalScore(), chunk.sourceRef()))
                .toList();
        GapDetectionResult gap = trace.gapDetectionResult();
        return new RagAnswer(
                trace.traceId(), trace.answer(), trace.answerConfidence(), usedKnowledge,
                gap.hasGap(), gap, trace.promptTokens(), trace.completionTokens(), trace.latencyMs());
    }

    private void safeRecord(RagTrace trace) {
        try {
            traceRepository.save(trace);
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.ERROR, "RagTrace persistence failed for trace " + trace.traceId(), exception);
        }
    }

    private void recordUsage(List<RetrievedChunk> used) {
        for (RetrievedChunk chunk : used) {
            try {
                knowledgeRepository.incrementUsageCount(chunk.knowledgeId());
            } catch (Exception exception) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "Knowledge usage update failed for " + chunk.knowledgeId(),
                        exception);
            }
        }
    }

    private boolean enabled(Boolean requestOverride, boolean defaultValue) {
        return requestOverride == null ? defaultValue : requestOverride;
    }

    private long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private void validate(RagRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new InvalidRagRequestException("question must not be blank");
        }
        if (request.topK() != null && request.topK() < 1) {
            throw new InvalidRagRequestException("topK must be positive");
        }
        if (request.minScore() != null && (request.minScore() < 0.0 || request.minScore() > 1.0)) {
            throw new InvalidRagRequestException("minScore must be between 0 and 1");
        }
    }
}
