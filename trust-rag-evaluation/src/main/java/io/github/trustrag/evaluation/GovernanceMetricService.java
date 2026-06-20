package io.github.trustrag.evaluation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class GovernanceMetricService {

    private final EvaluationRepository repository;
    private final Clock clock;

    public GovernanceMetricService(EvaluationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EvalGovernanceSnapshot capture(String tenantId, String projectId, LocalDate snapshotDate) {
        LocalDate date = snapshotDate == null ? LocalDate.now(clock) : snapshotDate;
        return repository.captureGovernanceSnapshot(tenantId, projectId, date, clock.instant());
    }

    public EvalGovernanceSnapshot latest(String tenantId, String projectId) {
        return repository.latestGovernanceSnapshot(tenantId, projectId)
                .orElseGet(() -> capture(tenantId, projectId, LocalDate.now(clock)));
    }

    public List<EvalGovernanceSnapshot> trend(String tenantId, String projectId, LocalDate from, LocalDate to) {
        return repository.listGovernanceSnapshots(tenantId, projectId, from, to);
    }
}
