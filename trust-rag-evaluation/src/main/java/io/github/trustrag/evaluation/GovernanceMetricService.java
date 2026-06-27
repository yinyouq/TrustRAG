package io.github.trustrag.evaluation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 知识治理指标服务。
 *
 * <p>负责生成和查询治理快照，让评估台可以观察候选批准率、复用率和污染风险趋势。</p>
 */
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
        // 没有历史快照时即时生成一份，保证概览页始终有可展示的基线。
        return repository.latestGovernanceSnapshot(tenantId, projectId)
                .orElseGet(() -> capture(tenantId, projectId, LocalDate.now(clock)));
    }

    public List<EvalGovernanceSnapshot> trend(String tenantId, String projectId, LocalDate from, LocalDate to) {
        return repository.listGovernanceSnapshots(tenantId, projectId, from, to);
    }
}
