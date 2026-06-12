package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.KnowledgeLineage;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JdbcKnowledgeLineageRepository implements KnowledgeLineageRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<KnowledgeLineage> rowMapper = this::mapRow;

    public JdbcKnowledgeLineageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeLineage save(KnowledgeLineage lineage) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO knowledge_lineage (
                    knowledge_id, parent_knowledge_id, source_trace_id, source_feedback_id,
                    action, operator_type, operator_id, created_at
                ) VALUES (
                    :knowledgeId, :parentKnowledgeId, :sourceTraceId, :sourceFeedbackId,
                    :action, :operatorType, :operatorId, :createdAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("knowledgeId", lineage.knowledgeId())
                        .addValue("parentKnowledgeId", lineage.parentKnowledgeId())
                        .addValue("sourceTraceId", lineage.sourceTraceId())
                        .addValue("sourceFeedbackId", lineage.sourceFeedbackId())
                        .addValue("action", lineage.action())
                        .addValue("operatorType", lineage.operatorType())
                        .addValue("operatorId", lineage.operatorId())
                        .addValue("createdAt", timestamp(lineage.createdAt())),
                keys,
                new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a lineage id");
        }
        return new KnowledgeLineage(
                key.longValue(), lineage.knowledgeId(), lineage.parentKnowledgeId(),
                lineage.sourceTraceId(), lineage.sourceFeedbackId(), lineage.action(),
                lineage.operatorType(), lineage.operatorId(), lineage.createdAt());
    }

    @Override
    public List<KnowledgeLineage> findByKnowledgeId(long knowledgeId) {
        return jdbc.query("""
                        SELECT * FROM knowledge_lineage
                        WHERE knowledge_id=:knowledgeId
                        ORDER BY created_at ASC
                        """,
                Map.of("knowledgeId", knowledgeId),
                rowMapper);
    }

    private KnowledgeLineage mapRow(ResultSet rs, int rowNum) throws SQLException {
        long parentId = rs.getLong("parent_knowledge_id");
        Long parent = rs.wasNull() ? null : parentId;
        long feedbackId = rs.getLong("source_feedback_id");
        Long feedback = rs.wasNull() ? null : feedbackId;
        return new KnowledgeLineage(
                rs.getLong("id"),
                rs.getLong("knowledge_id"),
                parent,
                rs.getString("source_trace_id"),
                feedback,
                rs.getString("action"),
                rs.getString("operator_type"),
                rs.getString("operator_id"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
