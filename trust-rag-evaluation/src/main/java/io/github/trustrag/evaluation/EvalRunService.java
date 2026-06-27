package io.github.trustrag.evaluation;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * EvalRunService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class EvalRunService {

    private final EvaluationRepository repository;
    private final EvalRunner runner;
    private final Executor executor;
    private final Clock clock;

    public EvalRunService(
            EvaluationRepository repository,
            EvalRunner runner,
            Executor executor,
            Clock clock) {
        this.repository = repository;
        this.runner = runner;
        this.executor = executor;
        this.clock = clock;
    }

    public EvalRun createRun(CreateEvalRunCommand command) {
        List<EvalCase> cases = repository.listCases(command.datasetId(), true, Integer.MAX_VALUE, 0);
        EvalRun run = repository.createRun(new EvalRun(
                null,
                command.datasetId(),
                command.runName(),
                command.runType() == null ? EvalRunType.MANUAL : command.runType(),
                command.beforeAfterGroup() == null ? BeforeAfterGroup.NORMAL : command.beforeAfterGroup(),
                EvalRunStatus.PENDING,
                cases.size(),
                0,
                0,
                command.engineConfigSnapshot(),
                command.modelConfigSnapshot(),
                clock.instant(),
                null,
                null,
                null,
                command.createdBy(),
                clock.instant()));
        if (command.async()) {
            executor.execute(() -> runner.run(run.id()));
        } else {
            runner.run(run.id());
        }
        return repository.findRun(run.id()).orElse(run);
    }

    public void cancel(long runId) {
        EvalRun run = repository.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("eval run not found: " + runId));
        repository.completeRun(
                runId,
                EvalRunStatus.CANCELED,
                run.successCount(),
                run.failedCount(),
                clock.instant(),
                null);
    }
}
