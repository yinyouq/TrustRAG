package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.IndexOperation;
import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexSyncTaskStatus;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
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

public final class JdbcIndexSyncTaskRepository implements IndexSyncTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<IndexSyncTask> rowMapper = this::mapRow;

    public JdbcIndexSyncTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public IndexSyncTask save(IndexSyncTask task) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO index_sync_task (
                    knowledge_id, target_index, operation, status, target_status,
                    retry_count, error_message, created_at, started_at, finished_at
                ) VALUES (
                    :knowledgeId, :targetIndex, :operation, :status, :targetStatus,
                    :retryCount, :errorMessage, :createdAt, :startedAt, :finishedAt
                )
                """, parameters(task), keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Database did not return an index sync task id");
        }
        return new IndexSyncTask(
                key.longValue(), task.knowledgeId(), task.targetIndex(),
                task.operation(), task.status(), task.targetStatus(),
                task.retryCount(), task.errorMessage(), task.createdAt(),
                task.startedAt(), task.finishedAt());
    }

    @Override
    public void update(IndexSyncTask task) {
        int updated = jdbc.update("""
                UPDATE index_sync_task SET
                    status=:status, retry_count=:retryCount,
                    error_message=:errorMessage, started_at=:startedAt,
                    finished_at=:finishedAt
                WHERE id=:id
                """, parameters(task).addValue("id", task.id()));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Index sync task update affected no row: " + task.id());
        }
    }

    @Override
    public boolean claim(IndexSyncTask task, Instant startedAt) {
        return jdbc.update("""
                        UPDATE index_sync_task SET
                            status='RUNNING', started_at=:startedAt,
                            error_message=NULL
                        WHERE id=:id AND status=:expectedStatus
                          AND retry_count=:retryCount
                        """,
                new MapSqlParameterSource()
                        .addValue("id", task.id())
                        .addValue("expectedStatus", task.status().name())
                        .addValue("retryCount", task.retryCount())
                        .addValue("startedAt", timestamp(startedAt))) == 1;
    }

    @Override
    public List<IndexSyncTask> findRunnable(int retryLimit, int limit) {
        return jdbc.query("""
                        SELECT * FROM index_sync_task
                        WHERE status='PENDING'
                           OR (status='FAILED' AND retry_count<:retryLimit)
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """,
                Map.of("retryLimit", retryLimit, "limit", limit),
                rowMapper);
    }

    @Override
    public List<IndexSyncTask> findActiveByKnowledgeId(long knowledgeId) {
        return jdbc.query("""
                        SELECT * FROM index_sync_task
                        WHERE knowledge_id=:knowledgeId
                          AND status IN ('PENDING', 'RUNNING', 'FAILED')
                        ORDER BY created_at ASC
                        """,
                Map.of("knowledgeId", knowledgeId),
                rowMapper);
    }

    private MapSqlParameterSource parameters(IndexSyncTask task) {
        return new MapSqlParameterSource()
                .addValue("knowledgeId", task.knowledgeId())
                .addValue("targetIndex", task.targetIndex().name())
                .addValue("operation", task.operation().name())
                .addValue("status", task.status().name())
                .addValue(
                        "targetStatus",
                        task.targetStatus() == null
                                ? null
                                : task.targetStatus().name())
                .addValue("retryCount", task.retryCount())
                .addValue("errorMessage", task.errorMessage())
                .addValue("createdAt", timestamp(task.createdAt()))
                .addValue("startedAt", timestamp(task.startedAt()))
                .addValue("finishedAt", timestamp(task.finishedAt()));
    }

    private IndexSyncTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        String targetStatus = rs.getString("target_status");
        return new IndexSyncTask(
                rs.getLong("id"),
                rs.getLong("knowledge_id"),
                IndexTarget.valueOf(rs.getString("target_index")),
                IndexOperation.valueOf(rs.getString("operation")),
                IndexSyncTaskStatus.valueOf(rs.getString("status")),
                targetStatus == null ? null : KnowledgeStatus.valueOf(targetStatus),
                rs.getInt("retry_count"),
                rs.getString("error_message"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
