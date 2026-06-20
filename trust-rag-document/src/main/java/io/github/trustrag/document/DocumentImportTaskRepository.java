package io.github.trustrag.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentImportTaskRepository {

    DocumentImportTask save(DocumentImportTask task);

    void update(DocumentImportTask task);

    Optional<DocumentImportTask> findByTaskId(String taskId);

    List<DocumentImportTask> findRunnable(int retryLimit, int limit);

    boolean claim(DocumentImportTask task, Instant now);
}
