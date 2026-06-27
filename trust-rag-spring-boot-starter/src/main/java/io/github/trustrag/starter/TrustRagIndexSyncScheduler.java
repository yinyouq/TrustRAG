package io.github.trustrag.starter;

import io.github.trustrag.core.service.IndexSyncWorker;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * TrustRagIndexSyncScheduler 是定时调度入口，按配置周期触发后台 Worker 或治理服务。
 */
public final class TrustRagIndexSyncScheduler {

    private final IndexSyncWorker worker;
    private final int batchSize;

    public TrustRagIndexSyncScheduler(IndexSyncWorker worker, int batchSize) {
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${trust-rag.index-sync.fixed-delay-ms:30000}",
            initialDelayString = "${trust-rag.index-sync.fixed-delay-ms:30000}")
    public void synchronizeIndexes() {
        worker.runBatch(batchSize);
    }
}
