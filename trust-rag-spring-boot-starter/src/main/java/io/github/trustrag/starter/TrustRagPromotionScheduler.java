package io.github.trustrag.starter;

import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.PromotionTaskService;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import org.springframework.scheduling.annotation.Scheduled;

public final class TrustRagPromotionScheduler {

    private final PromotionTaskService taskService;
    private final KnowledgePromotionWorker worker;
    private final KnowledgeLifecycleManager lifecycleManager;
    private final int batchSize;

    public TrustRagPromotionScheduler(
            PromotionTaskService taskService,
            KnowledgePromotionWorker worker,
            KnowledgeLifecycleManager lifecycleManager,
            int batchSize) {
        this.taskService = taskService;
        this.worker = worker;
        this.lifecycleManager = lifecycleManager;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${trust-rag.promotion.schedule:0 0 3 * * ?}")
    public void runGovernanceCycle() {
        taskService.scanAndCreate(batchSize);
        worker.runBatch(batchSize);
        lifecycleManager.expireDueKnowledge();
        lifecycleManager.downgradeNegativeFeedback();
        lifecycleManager.retryFailedIndexes();
    }
}
