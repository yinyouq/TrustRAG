package io.github.trustrag.evaluation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * EvaluationRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface EvaluationRepository {

    EvalDataset saveDataset(EvalDataset dataset);

    Optional<EvalDataset> findDataset(long datasetId);

    List<EvalDataset> listDatasets(String tenantId, String projectId, boolean includeDisabled, int limit, int offset);

    boolean datasetHasRuns(long datasetId);

    void deleteDataset(long datasetId);

    EvalCase saveCase(EvalCase evalCase);

    EvalCase saveCaseWithExpectedKnowledge(
            EvalCase evalCase, List<ExpectedKnowledge> expectedKnowledge);

    Optional<EvalCase> findCase(long evalCaseId);

    List<EvalCase> listCases(long datasetId, boolean onlyEnabled, int limit, int offset);

    boolean caseHasResults(long evalCaseId);

    void deleteCase(long evalCaseId);

    void replaceExpectedKnowledge(long evalCaseId, List<ExpectedKnowledge> expectedKnowledge);

    List<ExpectedKnowledge> listExpectedKnowledge(long evalCaseId);

    EvalRun createRun(EvalRun run);

    Optional<EvalRun> findRun(long runId);

    List<EvalRun> listRuns(Long datasetId, int limit, int offset);

    void markRunRunning(long runId, Instant startedAt);

    void updateRunProgress(long runId, int successCount, int failedCount);

    void completeRun(long runId, EvalRunStatus status, int successCount, int failedCount, Instant finishedAt, String errorMessage);

    EvalResult saveResult(EvalResult result);

    List<EvalResult> listResults(long runId, int limit, int offset);

    EvalJudgeDetail saveJudgeDetail(EvalJudgeDetail detail);

    List<EvalJudgeDetail> listJudgeDetails(long resultId);

    EvalReport saveReport(EvalReport report);

    Optional<EvalReport> findReportByRunId(long runId);

    EvalCompareReport saveCompareReport(EvalCompareReport report);

    EvalGovernanceSnapshot captureGovernanceSnapshot(String tenantId, String projectId, LocalDate snapshotDate, Instant createdAt);

    Optional<EvalGovernanceSnapshot> latestGovernanceSnapshot(String tenantId, String projectId);

    List<EvalGovernanceSnapshot> listGovernanceSnapshots(String tenantId, String projectId, LocalDate from, LocalDate to);
}
