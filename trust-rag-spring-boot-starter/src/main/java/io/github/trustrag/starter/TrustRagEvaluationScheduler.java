package io.github.trustrag.starter;

import io.github.trustrag.evaluation.GovernanceMetricService;
import org.springframework.scheduling.annotation.Scheduled;

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
