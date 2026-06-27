package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.IndexSyncTask;

import java.time.Instant;
import java.util.List;

/**
 * IndexSyncTaskRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface IndexSyncTaskRepository {

    IndexSyncTask save(IndexSyncTask task);

    void update(IndexSyncTask task);

    boolean claim(IndexSyncTask task, Instant startedAt);

    List<IndexSyncTask> findRunnable(int retryLimit, int limit);

    List<IndexSyncTask> findActiveByKnowledgeId(long knowledgeId);
}
