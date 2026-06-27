package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.spi.PromotionTaskRepository;
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

/**
 * JdbcPromotionTaskRepository 是关系型数据库仓储实现，负责对应业务数据的读写和状态更新。
 */
public final class JdbcPromotionTaskRepository implements PromotionTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<PromotionTask> rowMapper = this::mapRow;

    public JdbcPromotionTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PromotionTask save(PromotionTask task) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO promotion_task (
                    knowledge_id, status, task_type, retry_count, error_message,
                    started_at, finished_at, created_at
                ) VALUES (
                    :knowledgeId, :status, :taskType, :retryCount, :errorMessage,
                    :startedAt, :finishedAt, :createdAt
                )
                """, parameters(task), keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a promotion task id");
        }
        return new PromotionTask(
                key.longValue(), task.knowledgeId(), task.status(), task.taskType(),
                task.retryCount(), task.errorMessage(), task.startedAt(),
                task.finishedAt(), task.createdAt());
    }

    @Override
    public void update(PromotionTask task) {
        int updated = jdbc.update("""
                UPDATE promotion_task SET
                    status=:status, retry_count=:retryCount, error_message=:errorMessage,
                    started_at=:startedAt, finished_at=:finishedAt
                WHERE id=:id
                """, parameters(task).addValue("id", task.id()));
        if (updated != 1) {
            throw new IllegalStateException("Promotion task update affected no row: " + task.id());
        }
    }

    @Override
    public boolean claim(PromotionTask task, Instant startedAt) {
        return jdbc.update("""
                        UPDATE promotion_task SET
                            status='RUNNING', started_at=:startedAt, error_message=NULL
                        WHERE id=:id AND status=:expectedStatus AND retry_count=:retryCount
                        """,
                new MapSqlParameterSource()
                        .addValue("id", task.id())
                        .addValue("expectedStatus", task.status().name())
                        .addValue("retryCount", task.retryCount())
                        .addValue("startedAt", timestamp(startedAt))) == 1;
    }

    @Override
    public Optional<PromotionTask> findActiveByKnowledgeId(
            long knowledgeId,
            PromotionTaskType taskType) {
        List<PromotionTask> results = jdbc.query("""
                        SELECT * FROM promotion_task
                        WHERE knowledge_id=:knowledgeId AND task_type=:taskType
                          AND status IN ('PENDING', 'RUNNING', 'FAILED')
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                Map.of("knowledgeId", knowledgeId, "taskType", taskType.name()),
                rowMapper);
        return results.stream().findFirst();
    }

    @Override
    public List<PromotionTask> findRunnable(int retryLimit, int limit) {
        return jdbc.query("""
                        SELECT * FROM promotion_task
                        WHERE status='PENDING'
                           OR (status='FAILED' AND retry_count<:retryLimit)
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """,
                Map.of("retryLimit", retryLimit, "limit", limit),
                rowMapper);
    }

    @Override
    public List<PromotionTask> findRunnableByType(
            PromotionTaskType taskType,
            int retryLimit,
            int limit) {
        return jdbc.query("""
                        SELECT * FROM promotion_task
                        WHERE task_type=:taskType
                          AND (status='PENDING'
                            OR (status='FAILED' AND retry_count<:retryLimit))
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """,
                Map.of(
                        "taskType", taskType.name(),
                        "retryLimit", retryLimit,
                        "limit", limit),
                rowMapper);
    }

    @Override
    public List<PromotionTask> find(
            PromotionTaskStatus status,
            PromotionTaskType taskType,
            Long knowledgeId,
            int limit,
            int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM promotion_task WHERE 1=1");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (status != null) {
            sql.append(" AND status=:status");
            parameters.addValue("status", status.name());
        }
        if (taskType != null) {
            sql.append(" AND task_type=:taskType");
            parameters.addValue("taskType", taskType.name());
        }
        if (knowledgeId != null) {
            sql.append(" AND knowledge_id=:knowledgeId");
            parameters.addValue("knowledgeId", knowledgeId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), parameters, rowMapper);
    }

    private MapSqlParameterSource parameters(PromotionTask task) {
        return new MapSqlParameterSource()
                .addValue("knowledgeId", task.knowledgeId())
                .addValue("status", task.status().name())
                .addValue("taskType", task.taskType().name())
                .addValue("retryCount", task.retryCount())
                .addValue("errorMessage", task.errorMessage())
                .addValue("startedAt", timestamp(task.startedAt()))
                .addValue("finishedAt", timestamp(task.finishedAt()))
                .addValue("createdAt", timestamp(task.createdAt()));
    }

    private PromotionTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PromotionTask(
                rs.getLong("id"),
                rs.getLong("knowledge_id"),
                PromotionTaskStatus.valueOf(rs.getString("status")),
                PromotionTaskType.valueOf(rs.getString("task_type")),
                rs.getInt("retry_count"),
                rs.getString("error_message"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
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
