package io.github.trustrag.storage.jdbc;

import io.github.trustrag.evaluation.EvalTraceReader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

public final class JdbcEvalTraceReader implements EvalTraceReader {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEvalTraceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Long> findRetrievedKnowledgeIds(String traceId) {
        return jdbc.queryForList("""
                        SELECT knowledge_id FROM retrieval_log
                        WHERE trace_id=:traceId AND knowledge_id IS NOT NULL
                        ORDER BY rank_no ASC, id ASC
                        """,
                Map.of("traceId", traceId),
                Long.class);
    }

    @Override
    public List<Long> findUsedKnowledgeIds(String traceId) {
        return jdbc.queryForList("""
                        SELECT knowledge_id FROM retrieval_log
                        WHERE trace_id=:traceId AND used_in_prompt=TRUE AND knowledge_id IS NOT NULL
                        ORDER BY rank_no ASC, id ASC
                        """,
                Map.of("traceId", traceId),
                Long.class);
    }
}
