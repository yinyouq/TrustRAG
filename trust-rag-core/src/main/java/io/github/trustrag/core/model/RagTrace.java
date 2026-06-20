package io.github.trustrag.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RagTrace {

    private final String traceId;
    private final Instant createdAt;
    private final String userId;
    private final String conversationId;
    private final String projectId;
    private final String tenantId;
    private final String originalQuestion;
    private final TraceType traceType;
    private final Long evalRunId;
    private final Long evalCaseId;
    private List<String> rewrittenQueries = List.of();
    private List<RetrievedChunk> retrievedChunks = List.of();
    private List<RetrievedChunk> rerankedChunks = List.of();
    private List<RetrievedChunk> usedChunks = List.of();
    private String finalPrompt;
    private String answer;
    private Double answerConfidence;
    private GapDetectionResult gapDetectionResult = GapDetectionResult.noGap();
    private long latencyMs;
    private int promptTokens;
    private int completionTokens;
    private TraceStatus status = TraceStatus.RUNNING;
    private String errorMessage;

    private RagTrace(RagRequest request, Instant now) {
        this.traceId = UUID.randomUUID().toString();
        this.createdAt = now;
        this.userId = request.userId();
        this.conversationId = request.conversationId();
        this.projectId = request.projectId();
        this.tenantId = request.tenantId();
        this.originalQuestion = request.question();
        this.traceType = request.isEvaluationMode() ? TraceType.EVAL : TraceType.NORMAL;
        this.evalRunId = request.evalRunId();
        this.evalCaseId = request.evalCaseId();
    }

    public static RagTrace start(RagRequest request, Instant now) {
        return new RagTrace(Objects.requireNonNull(request, "request"), Objects.requireNonNull(now, "now"));
    }

    public void rewrittenQueries(List<String> value) {
        this.rewrittenQueries = immutable(value);
    }

    public void retrievedChunks(List<RetrievedChunk> value) {
        this.retrievedChunks = immutable(value);
    }

    public void rerankedChunks(List<RetrievedChunk> value) {
        this.rerankedChunks = immutable(value);
    }

    public void usedChunks(List<RetrievedChunk> value) {
        this.usedChunks = immutable(value);
    }

    public void finalPrompt(String value) {
        this.finalPrompt = value;
    }

    public void complete(LlmResponse response, GapDetectionResult gap, long elapsedMs) {
        this.answer = response.text();
        this.answerConfidence = response.confidence();
        this.promptTokens = response.tokenUsage().promptTokens();
        this.completionTokens = response.tokenUsage().completionTokens();
        this.gapDetectionResult = gap == null ? GapDetectionResult.noGap() : gap;
        this.latencyMs = elapsedMs;
        this.status = TraceStatus.SUCCEEDED;
    }

    public void fail(Throwable error, long elapsedMs) {
        this.latencyMs = elapsedMs;
        this.status = TraceStatus.FAILED;
        this.errorMessage = error == null ? "Unknown error" : abbreviate(error.getMessage(), 2000);
    }

    public double maxVectorScore() {
        return retrievedChunks.stream().mapToDouble(RetrievedChunk::vectorScore).max().orElse(0.0);
    }

    public double avgVectorScore() {
        return retrievedChunks.stream().mapToDouble(RetrievedChunk::vectorScore).average().orElse(0.0);
    }

    public double maxRerankScore() {
        return rerankedChunks.stream()
                .map(RetrievedChunk::rerankScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    public boolean noContext() {
        return usedChunks.isEmpty();
    }

    public boolean onlyLowTrustMatched() {
        return !retrievedChunks.isEmpty()
                && retrievedChunks.stream().allMatch(chunk -> chunk.trustLevel() == TrustLevel.LOW);
    }

    public boolean usedLowTrustKnowledge() {
        return usedChunks.stream().anyMatch(chunk -> chunk.trustLevel() == TrustLevel.LOW);
    }

    public String traceId() {
        return traceId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String userId() {
        return userId;
    }

    public String conversationId() {
        return conversationId;
    }

    public String projectId() {
        return projectId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String originalQuestion() {
        return originalQuestion;
    }

    public TraceType traceType() {
        return traceType;
    }

    public Long evalRunId() {
        return evalRunId;
    }

    public Long evalCaseId() {
        return evalCaseId;
    }

    public List<String> rewrittenQueries() {
        return rewrittenQueries;
    }

    public List<RetrievedChunk> retrievedChunks() {
        return retrievedChunks;
    }

    public List<RetrievedChunk> rerankedChunks() {
        return rerankedChunks;
    }

    public List<RetrievedChunk> usedChunks() {
        return usedChunks;
    }

    public String finalPrompt() {
        return finalPrompt;
    }

    public String answer() {
        return answer;
    }

    public Double answerConfidence() {
        return answerConfidence;
    }

    public GapDetectionResult gapDetectionResult() {
        return gapDetectionResult;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public TraceStatus status() {
        return status;
    }

    public String errorMessage() {
        return errorMessage;
    }

    private static <T> List<T> immutable(List<T> source) {
        return source == null ? List.of() : List.copyOf(new ArrayList<>(source));
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
