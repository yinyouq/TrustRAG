package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.ReviewStatus;
import io.github.trustrag.core.model.ReviewTask;
import io.github.trustrag.core.spi.ReviewTaskRepository;
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
import java.util.Optional;

public final class JdbcReviewTaskRepository implements ReviewTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<ReviewTask> rowMapper = this::mapRow;

    public JdbcReviewTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReviewTask save(ReviewTask task) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO review_task (
                    knowledge_id, status, reviewer_id, review_action,
                    review_comment, created_at, reviewed_at
                ) VALUES (
                    :knowledgeId, :status, :reviewerId, :reviewAction,
                    :reviewComment, :createdAt, :reviewedAt
                )
                """, parameters(task), keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated review task id");
        }
        return new ReviewTask(
                key.longValue(), task.knowledgeId(), task.status(), task.reviewerId(),
                task.reviewAction(), task.reviewComment(), task.createdAt(), task.reviewedAt());
    }

    @Override
    public void update(ReviewTask task) {
        int updated = jdbc.update("""
                UPDATE review_task SET
                    status=:status, reviewer_id=:reviewerId, review_action=:reviewAction,
                    review_comment=:reviewComment, reviewed_at=:reviewedAt
                WHERE id=:id
                """, parameters(task).addValue("id", task.id()));
        if (updated != 1) {
            throw new IllegalStateException("Review task update affected no row: " + task.id());
        }
    }

    @Override
    public Optional<ReviewTask> findPendingByKnowledgeId(long knowledgeId) {
        List<ReviewTask> results = jdbc.query("""
                        SELECT * FROM review_task
                        WHERE knowledge_id=:knowledgeId AND status=:status
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                Map.of("knowledgeId", knowledgeId, "status", ReviewStatus.PENDING.name()),
                rowMapper);
        return results.stream().findFirst();
    }

    private MapSqlParameterSource parameters(ReviewTask task) {
        return new MapSqlParameterSource()
                .addValue("knowledgeId", task.knowledgeId())
                .addValue("status", task.status().name())
                .addValue("reviewerId", task.reviewerId())
                .addValue("reviewAction", task.reviewAction())
                .addValue("reviewComment", task.reviewComment())
                .addValue("createdAt", timestamp(task.createdAt()))
                .addValue("reviewedAt", timestamp(task.reviewedAt()));
    }

    private ReviewTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewTask(
                rs.getLong("id"),
                rs.getLong("knowledge_id"),
                ReviewStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_id"),
                rs.getString("review_action"),
                rs.getString("review_comment"),
                instant(rs, "created_at"),
                instant(rs, "reviewed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
