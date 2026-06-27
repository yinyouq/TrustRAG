package io.github.trustrag.starter;

import io.github.trustrag.evaluation.GovernanceMetricService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * TrustRagEvaluationScheduler 是定时调度入口，按配置周期触发后台 Worker 或治理服务。
 */
public final class TrustRagEvaluationScheduler {

    private final GovernanceMetricService governanceMetricService;

    public TrustRagEvaluationScheduler(GovernanceMetricService governanceMetricService) {
        this.governanceMetricService = governanceMetricService;
    }

    @Scheduled(cron = "${trust-rag.evaluation.governance.snapshot-cron:0 0 2 * * ?}")
    public void captureGovernanceSnapshot() {
        governanceMetricService.capture(null, null, null);
    }
}
