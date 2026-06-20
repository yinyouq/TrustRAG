package io.github.trustrag.starter;

import io.github.trustrag.core.service.IndexSyncWorker;
import org.springframework.scheduling.annotation.Scheduled;

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
