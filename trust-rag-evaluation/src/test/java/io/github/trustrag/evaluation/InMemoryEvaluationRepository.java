package io.github.trustrag.evaluation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 评估单元测试使用的内存仓储，覆盖 EvaluationRepository 的主要读写路径。
 */
class InMemoryEvaluationRepository implements EvaluationRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, EvalDataset> datasets = new LinkedHashMap<>();
    private final Map<Long, EvalCase> cases = new LinkedHashMap<>();
    private final Map<Long, List<ExpectedKnowledge>> expectedKnowledge = new LinkedHashMap<>();
    private final Map<Long, EvalRun> runs = new LinkedHashMap<>();
    private final Map<Long, EvalResult> results = new LinkedHashMap<>();
    private final Map<Long, EvalReport> reports = new LinkedHashMap<>();
    private final Map<Long, List<EvalJudgeDetail>> judgeDetails = new LinkedHashMap<>();
    private final List<EvalGovernanceSnapshot> snapshots = new ArrayList<>();

    @Override
    public EvalDataset saveDataset(EvalDataset dataset) {
        EvalDataset saved = dataset.id() == null ? dataset.withId(ids.getAndIncrement()) : dataset;
        datasets.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<EvalDataset> findDataset(long datasetId) {
        return Optional.ofNullable(datasets.get(datasetId));
    }

    @Override
    public List<EvalDataset> listDatasets(
            String tenantId, String projectId, boolean includeDisabled, int limit, int offset) {
        return datasets.values().stream()
                .filter(item -> includeDisabled || item.enabled())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public boolean datasetHasRuns(long datasetId) {
        return runs.values().stream().anyMatch(run -> run.datasetId() == datasetId);
    }

    @Override
    public void deleteDataset(long datasetId) {
        cases.values().stream()
                .filter(item -> item.datasetId() == datasetId)
                .map(EvalCase::id)
                .toList()
                .forEach(this::deleteCase);
        datasets.remove(datasetId);
    }

    @Override
    public EvalCase saveCase(EvalCase evalCase) {
        EvalCase saved = evalCase.id() == null ? evalCase.withId(ids.getAndIncrement()) : evalCase;
        cases.put(saved.id(), saved);
        return saved;
    }

    @Override
    public EvalCase saveCaseWithExpectedKnowledge(
            EvalCase evalCase, List<ExpectedKnowledge> expectedKnowledge) {
        EvalCase saved = saveCase(evalCase);
        replaceExpectedKnowledge(saved.id(), expectedKnowledge);
        return saved;
    }

    @Override
    public Optional<EvalCase> findCase(long evalCaseId) {
        return Optional.ofNullable(cases.get(evalCaseId));
    }

    @Override
    public List<EvalCase> listCases(long datasetId, boolean onlyEnabled, int limit, int offset) {
        return cases.values().stream()
                .filter(item -> item.datasetId() == datasetId)
                .filter(item -> !onlyEnabled || item.enabled())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public boolean caseHasResults(long evalCaseId) {
        return results.values().stream().anyMatch(result -> result.evalCaseId() == evalCaseId);
    }

    @Override
    public void deleteCase(long evalCaseId) {
        expectedKnowledge.remove(evalCaseId);
        cases.remove(evalCaseId);
    }

    @Override
    public void replaceExpectedKnowledge(long evalCaseId, List<ExpectedKnowledge> values) {
        expectedKnowledge.put(evalCaseId, List.copyOf(values));
    }

    @Override
    public List<ExpectedKnowledge> listExpectedKnowledge(long evalCaseId) {
        return expectedKnowledge.getOrDefault(evalCaseId, List.of());
    }

    @Override
    public EvalRun createRun(EvalRun run) {
        EvalRun saved = run.id() == null ? run.withId(ids.getAndIncrement()) : run;
        runs.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<EvalRun> findRun(long runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<EvalRun> listRuns(Long datasetId, int limit, int offset) {
        return runs.values().stream()
                .filter(run -> datasetId == null || run.datasetId().equals(datasetId))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public void markRunRunning(long runId, Instant startedAt) {
        EvalRun run = runs.get(runId);
        runs.put(runId, copyRun(run, EvalRunStatus.RUNNING, run.successCount(), run.failedCount(),
                startedAt, run.finishedAt(), run.errorMessage()));
    }

    @Override
    public void updateRunProgress(long runId, int successCount, int failedCount) {
        EvalRun run = runs.get(runId);
        runs.put(runId, copyRun(run, run.status(), successCount, failedCount,
                run.startedAt(), run.finishedAt(), run.errorMessage()));
    }

    @Override
    public void completeRun(
            long runId, EvalRunStatus status, int successCount, int failedCount,
            Instant finishedAt, String errorMessage) {
        EvalRun run = runs.get(runId);
        runs.put(runId, copyRun(run, status, successCount, failedCount,
                run.startedAt(), finishedAt, errorMessage));
    }

    @Override
    public EvalResult saveResult(EvalResult result) {
        EvalResult saved = result.id() == null ? result.withId(ids.getAndIncrement()) : result;
        results.put(saved.id(), saved);
        return saved;
    }

    @Override
    public List<EvalResult> listResults(long runId, int limit, int offset) {
        return results.values().stream()
                .filter(result -> result.evalRunId() == runId)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public EvalJudgeDetail saveJudgeDetail(EvalJudgeDetail detail) {
        EvalJudgeDetail saved = detail.id() == null ? detail.withId(ids.getAndIncrement()) : detail;
        judgeDetails.computeIfAbsent(saved.evalResultId(), ignored -> new ArrayList<>()).add(saved);
        return saved;
    }

    @Override
    public List<EvalJudgeDetail> listJudgeDetails(long resultId) {
        return judgeDetails.getOrDefault(resultId, List.of());
    }

    @Override
    public EvalReport saveReport(EvalReport report) {
        EvalReport saved = report.id() == null ? report.withId(ids.getAndIncrement()) : report;
        reports.put(saved.evalRunId(), saved);
        return saved;
    }

    @Override
    public Optional<EvalReport> findReportByRunId(long runId) {
        return Optional.ofNullable(reports.get(runId));
    }

    @Override
    public EvalCompareReport saveCompareReport(EvalCompareReport report) {
        return report.id() == null ? report.withId(ids.getAndIncrement()) : report;
    }

    @Override
    public EvalGovernanceSnapshot captureGovernanceSnapshot(
            String tenantId, String projectId, LocalDate snapshotDate, Instant createdAt) {
        EvalGovernanceSnapshot snapshot = new EvalGovernanceSnapshot(
                ids.getAndIncrement(), tenantId, projectId, snapshotDate,
                0, 0, 0, null, null, null, null, null, createdAt);
        snapshots.add(snapshot);
        return snapshot;
    }

    @Override
    public Optional<EvalGovernanceSnapshot> latestGovernanceSnapshot(String tenantId, String projectId) {
        return snapshots.stream().reduce((left, right) -> right);
    }

    @Override
    public List<EvalGovernanceSnapshot> listGovernanceSnapshots(
            String tenantId, String projectId, LocalDate from, LocalDate to) {
        return List.copyOf(snapshots);
    }

    private EvalRun copyRun(
            EvalRun run, EvalRunStatus status, int successCount, int failedCount,
            Instant startedAt, Instant finishedAt, String errorMessage) {
        return new EvalRun(
                run.id(), run.datasetId(), run.runName(), run.runType(), run.beforeAfterGroup(),
                status, run.totalCount(), successCount, failedCount, run.engineConfigSnapshot(),
                run.modelConfigSnapshot(), run.knowledgeSnapshotTime(), startedAt, finishedAt,
                errorMessage, run.createdBy(), run.createdAt());
    }
}
