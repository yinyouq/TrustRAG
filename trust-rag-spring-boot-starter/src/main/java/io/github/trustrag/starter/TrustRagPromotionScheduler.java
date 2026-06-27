package io.github.trustrag.starter;

import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.PromotionTaskService;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * TrustRagPromotionScheduler 是定时调度入口，按配置周期触发后台 Worker 或治理服务。
 */
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
