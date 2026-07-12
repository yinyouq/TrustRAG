package io.github.trustrag.storage.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.SearchType;
import io.github.trustrag.core.spi.RagTraceRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JdbcRagTraceRepository 是关系型数据库仓储实现，负责对应业务数据的读写和状态更新。
 */
public class JdbcRagTraceRepository implements RagTraceRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcRagTraceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(RagTrace trace) {
        jdbc.update("""
                INSERT INTO rag_trace (
                    trace_id, user_id, conversation_id, project_id, tenant_id,
                    trace_type, eval_run_id, eval_case_id,
                    question, rewritten_query, final_prompt, answer,
                    max_vector_score, avg_vector_score, max_rerank_score, answer_confidence,
                    possible_gap, gap_score, gap_types, gap_reason,
                    prompt_tokens, completion_tokens, latency_ms, status, error_message, created_at
                ) VALUES (
                    :traceId, :userId, :conversationId, :projectId, :tenantId,
                    :traceType, :evalRunId, :evalCaseId,
                    :question, :rewrittenQuery, :finalPrompt, :answer,
                    :maxVectorScore, :avgVectorScore, :maxRerankScore, :answerConfidence,
                    :possibleGap, :gapScore, :gapTypes, :gapReason,
                    :promptTokens, :completionTokens, :latencyMs, :status, :errorMessage, :createdAt
                )
                """, traceParameters(trace));
        saveRetrievalLogs(trace);
    }

    @Override
    public boolean existsByTraceId(String traceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_trace WHERE trace_id=:traceId",
                Map.of("traceId", traceId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Optional<String> findQuestionByTraceId(String traceId) {
        List<String> questions = jdbc.queryForList(
                "SELECT question FROM rag_trace WHERE trace_id=:traceId",
                Map.of("traceId", traceId),
                String.class);
        return questions.stream().findFirst();
    }

    @Override
    public List<Long> findUsedKnowledgeIds(String traceId) {
        return jdbc.queryForList("""
                        SELECT knowledge_id FROM retrieval_log
                        WHERE trace_id=:traceId AND used_in_prompt=TRUE AND knowledge_id IS NOT NULL
                        """,
                Map.of("traceId", traceId),
                Long.class);
    }

    private void saveRetrievalLogs(RagTrace trace) {
        List<RetrievedChunk> ranked = trace.rerankedChunks().isEmpty()
                ? trace.retrievedChunks()
                : trace.rerankedChunks();
        if (ranked.isEmpty()) {
            return;
        }
        Set<Long> usedIds = new HashSet<>();
        trace.usedChunks().forEach(chunk -> usedIds.add(chunk.knowledgeId()));
        String query = String.join("\n", trace.rewrittenQueries());
        String sql = """
                INSERT INTO retrieval_log (
                    trace_id, knowledge_id, query, search_type, vector_score, keyword_score,
                    vector_rank, keyword_rank, rrf_score, rerank_score, trust_score,
                    final_score, trust_level, scope_type, rank_no, used_in_prompt, created_at
                ) VALUES (
                    :traceId, :knowledgeId, :query, :searchType, :vectorScore, :keywordScore,
                    :vectorRank, :keywordRank, :rrfScore, :rerankScore, :trustScore,
                    :finalScore, :trustLevel, :scopeType, :rankNo, :usedInPrompt, :createdAt
                )
                """;
        MapSqlParameterSource[] batch = new MapSqlParameterSource[ranked.size()];
        for (int index = 0; index < ranked.size(); index++) {
            RetrievedChunk chunk = ranked.get(index);
            batch[index] = new MapSqlParameterSource()
                    .addValue("traceId", trace.traceId())
                    .addValue("knowledgeId", chunk.knowledgeId())
                    .addValue("query", query)
                    .addValue("searchType", chunk.searchType().name())
                    .addValue(
                            "vectorScore",
                            chunk.searchType() == SearchType.KEYWORD_ONLY
                                    ? null
                                    : chunk.vectorScore())
                    .addValue("keywordScore", chunk.keywordScore())
                    .addValue("vectorRank", chunk.vectorRank())
                    .addValue("keywordRank", chunk.keywordRank())
                    .addValue("rrfScore", chunk.rrfScore())
                    .addValue("rerankScore", chunk.rerankScore())
                    .addValue("trustScore", chunk.trustScore())
                    .addValue("finalScore", chunk.finalScore())
                    .addValue("trustLevel", chunk.trustLevel().name())
                    .addValue("scopeType", chunk.scopeType().name())
                    .addValue("rankNo", index + 1)
                    .addValue("usedInPrompt", usedIds.contains(chunk.knowledgeId()))
                    .addValue("createdAt", timestamp(trace.createdAt()));
        }
        jdbc.batchUpdate(sql, batch);
    }

    private MapSqlParameterSource traceParameters(RagTrace trace) {
        return new MapSqlParameterSource()
                .addValue("traceId", trace.traceId())
                .addValue("userId", trace.userId())
                .addValue("conversationId", trace.conversationId())
                .addValue("projectId", trace.projectId())
                .addValue("tenantId", trace.tenantId())
                .addValue("traceType", trace.traceType().name())
                .addValue("evalRunId", trace.evalRunId())
                .addValue("evalCaseId", trace.evalCaseId())
                .addValue("question", trace.originalQuestion())
                .addValue("rewrittenQuery", json(trace.rewrittenQueries()))
                .addValue("finalPrompt", trace.finalPrompt())
                .addValue("answer", trace.answer())
                .addValue("maxVectorScore", trace.maxVectorScore())
                .addValue("avgVectorScore", trace.avgVectorScore())
                .addValue("maxRerankScore", trace.maxRerankScore())
                .addValue("answerConfidence", trace.answerConfidence())
                .addValue("possibleGap", trace.gapDetectionResult().hasGap())
                .addValue("gapScore", trace.gapDetectionResult().gapScore())
                .addValue("gapTypes", json(trace.gapDetectionResult().gapTypes()))
                .addValue("gapReason", trace.gapDetectionResult().reason())
                .addValue("promptTokens", trace.promptTokens())
                .addValue("completionTokens", trace.completionTokens())
                .addValue("latencyMs", trace.latencyMs())
                .addValue("status", trace.status().name())
                .addValue("errorMessage", trace.errorMessage())
                .addValue("createdAt", timestamp(trace.createdAt()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Trace JSON serialization failed", exception);
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
