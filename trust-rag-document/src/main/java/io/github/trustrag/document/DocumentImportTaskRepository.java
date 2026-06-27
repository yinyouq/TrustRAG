package io.github.trustrag.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DocumentImportTaskRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface DocumentImportTaskRepository {

    DocumentImportTask save(DocumentImportTask task);

    void update(DocumentImportTask task);

    Optional<DocumentImportTask> findByTaskId(String taskId);

    List<DocumentImportTask> findRunnable(int retryLimit, int limit);

    boolean claim(DocumentImportTask task, Instant now);
}
