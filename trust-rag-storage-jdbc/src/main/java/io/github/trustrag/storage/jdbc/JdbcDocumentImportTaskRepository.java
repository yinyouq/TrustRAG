package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.document.DocumentImportOptions;
import io.github.trustrag.document.DocumentImportStatus;
import io.github.trustrag.document.DocumentImportTask;
import io.github.trustrag.document.DocumentImportTaskRepository;
import io.github.trustrag.document.DocumentSourceKind;
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

public final class JdbcDocumentImportTaskRepository implements DocumentImportTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<DocumentImportTask> rowMapper = this::mapRow;

    public JdbcDocumentImportTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DocumentImportTask save(DocumentImportTask task) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO document_import_task (
                    task_id, source_kind, status, original_filename, content_type,
                    source_uri, storage_path, git_ref, title, source_type,
                    trust_level, scope_type, user_id, conversation_id, project_id,
                    tenant_id, total_documents, total_sections, imported_count,
                    duplicate_count, failed_count, retry_count, error_message,
                    created_at, started_at, finished_at, updated_at
                ) VALUES (
                    :taskId, :sourceKind, :status, :originalFilename, :contentType,
                    :sourceUri, :storagePath, :gitRef, :title, :sourceType,
                    :trustLevel, :scopeType, :userId, :conversationId, :projectId,
                    :tenantId, :totalDocuments, :totalSections, :importedCount,
                    :duplicateCount, :failedCount, :retryCount, :errorMessage,
                    :createdAt, :startedAt, :finishedAt, :updatedAt
                )
                """, parameters(task), keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Database did not return a document import task id");
        }
        return task.withId(key.longValue());
    }

    @Override
    public void update(DocumentImportTask task) {
        int updated = jdbc.update("""
                UPDATE document_import_task SET
                    status=:status, total_documents=:totalDocuments,
                    total_sections=:totalSections, imported_count=:importedCount,
                    duplicate_count=:duplicateCount, failed_count=:failedCount,
                    retry_count=:retryCount, error_message=:errorMessage,
                    started_at=:startedAt, finished_at=:finishedAt, updated_at=:updatedAt
                WHERE id=:id
                """, parameters(task).addValue("id", task.id()));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Document import task update affected no row: " + task.id());
        }
    }

    @Override
    public Optional<DocumentImportTask> findByTaskId(String taskId) {
        return jdbc.query(
                        "SELECT * FROM document_import_task WHERE task_id=:taskId",
                        Map.of("taskId", taskId),
                        rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public List<DocumentImportTask> findRunnable(int retryLimit, int limit) {
        return jdbc.query("""
                        SELECT * FROM document_import_task
                        WHERE status='PENDING'
                           OR (status='FAILED' AND retry_count<:retryLimit)
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """,
                Map.of("retryLimit", retryLimit, "limit", limit),
                rowMapper);
    }

    @Override
    public boolean claim(DocumentImportTask task, Instant now) {
        return jdbc.update("""
                        UPDATE document_import_task SET
                            status='PROCESSING', started_at=:startedAt,
                            finished_at=NULL, error_message=NULL, updated_at=:startedAt
                        WHERE id=:id AND status=:expectedStatus
                          AND retry_count=:retryCount
                        """,
                new MapSqlParameterSource()
                        .addValue("id", task.id())
                        .addValue("expectedStatus", task.status().name())
                        .addValue("retryCount", task.retryCount())
                        .addValue("startedAt", timestamp(now))) == 1;
    }

    private MapSqlParameterSource parameters(DocumentImportTask task) {
        DocumentImportOptions options = task.options();
        return new MapSqlParameterSource()
                .addValue("taskId", task.taskId())
                .addValue("sourceKind", task.sourceKind().name())
                .addValue("status", task.status().name())
                .addValue("originalFilename", task.originalFilename())
                .addValue("contentType", task.contentType())
                .addValue("sourceUri", task.sourceUri())
                .addValue("storagePath", task.storagePath())
                .addValue("gitRef", task.gitRef())
                .addValue("title", options.title())
                .addValue("sourceType", options.sourceType())
                .addValue("trustLevel", options.trustLevel().name())
                .addValue("scopeType", options.scopeType().name())
                .addValue("userId", options.userId())
                .addValue("conversationId", options.conversationId())
                .addValue("projectId", options.projectId())
                .addValue("tenantId", options.tenantId())
                .addValue("totalDocuments", task.totalDocuments())
                .addValue("totalSections", task.totalSections())
                .addValue("importedCount", task.importedCount())
                .addValue("duplicateCount", task.duplicateCount())
                .addValue("failedCount", task.failedCount())
                .addValue("retryCount", task.retryCount())
                .addValue("errorMessage", task.errorMessage())
                .addValue("createdAt", timestamp(task.createdAt()))
                .addValue("startedAt", timestamp(task.startedAt()))
                .addValue("finishedAt", timestamp(task.finishedAt()))
                .addValue("updatedAt", timestamp(task.updatedAt()));
    }

    private DocumentImportTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentImportTask(
                rs.getLong("id"),
                rs.getString("task_id"),
                DocumentSourceKind.valueOf(rs.getString("source_kind")),
                DocumentImportStatus.valueOf(rs.getString("status")),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getString("source_uri"),
                rs.getString("storage_path"),
                rs.getString("git_ref"),
                new DocumentImportOptions(
                        rs.getString("title"),
                        rs.getString("source_type"),
                        TrustLevel.valueOf(rs.getString("trust_level")),
                        ScopeType.valueOf(rs.getString("scope_type")),
                        rs.getString("user_id"),
                        rs.getString("conversation_id"),
                        rs.getString("project_id"),
                        rs.getString("tenant_id")),
                rs.getInt("total_documents"),
                rs.getInt("total_sections"),
                rs.getInt("imported_count"),
                rs.getInt("duplicate_count"),
                rs.getInt("failed_count"),
                rs.getInt("retry_count"),
                rs.getString("error_message"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
