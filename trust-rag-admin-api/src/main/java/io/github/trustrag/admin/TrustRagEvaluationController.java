package io.github.trustrag.admin;

import io.github.trustrag.evaluation.BeforeAfterGroup;
import io.github.trustrag.evaluation.CreateEvalRunCommand;
import io.github.trustrag.evaluation.EvalCase;
import io.github.trustrag.evaluation.EvalCaseCsvImportResult;
import io.github.trustrag.evaluation.EvalCaseCsvImportService;
import io.github.trustrag.evaluation.EvalCaseService;
import io.github.trustrag.evaluation.EvalCompareReport;
import io.github.trustrag.evaluation.EvalDataset;
import io.github.trustrag.evaluation.EvalDatasetService;
import io.github.trustrag.evaluation.EvalGovernanceSnapshot;
import io.github.trustrag.evaluation.EvalJudgeDetail;
import io.github.trustrag.evaluation.EvalReport;
import io.github.trustrag.evaluation.EvalReportService;
import io.github.trustrag.evaluation.EvalResult;
import io.github.trustrag.evaluation.EvalRun;
import io.github.trustrag.evaluation.EvalRunService;
import io.github.trustrag.evaluation.EvalRunType;
import io.github.trustrag.evaluation.EvaluationRepository;
import io.github.trustrag.evaluation.ExpectedKnowledge;
import io.github.trustrag.evaluation.GovernanceMetricService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/trust-rag/admin/eval")
public final class TrustRagEvaluationController {

    private final EvalDatasetService datasetService;
    private final EvalCaseService caseService;
    private final EvalCaseCsvImportService csvImportService;
    private final EvalRunService runService;
    private final EvalReportService reportService;
    private final GovernanceMetricService governanceService;
    private final EvaluationRepository repository;

    public TrustRagEvaluationController(
            EvalDatasetService datasetService,
            EvalCaseService caseService,
            EvalCaseCsvImportService csvImportService,
            EvalRunService runService,
            EvalReportService reportService,
            GovernanceMetricService governanceService,
            EvaluationRepository repository) {
        this.datasetService = datasetService;
        this.caseService = caseService;
        this.csvImportService = csvImportService;
        this.runService = runService;
        this.reportService = reportService;
        this.governanceService = governanceService;
        this.repository = repository;
    }

    @PostMapping("/datasets")
    @ResponseStatus(HttpStatus.CREATED)
    public EvalDataset createDataset(@Valid @RequestBody DatasetRequest request) {
        return datasetService.create(
                request.name(),
                request.description(),
                request.tenantId(),
                request.projectId(),
                request.createdBy(),
                request.enabled() == null || request.enabled());
    }

    @GetMapping("/datasets")
    public List<EvalDataset> listDatasets(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return datasetService.list(tenantId, projectId, includeDisabled, limit, offset);
    }

    @GetMapping("/datasets/{id}")
    public EvalDataset getDataset(@PathVariable long id) {
        return datasetService.get(id);
    }

    @PutMapping("/datasets/{id}")
    public EvalDataset updateDataset(
            @PathVariable long id,
            @Valid @RequestBody DatasetRequest request) {
        boolean enabled = request.enabled() == null
                ? datasetService.get(id).enabled()
                : request.enabled();
        return datasetService.update(
                id, request.name(), request.description(), request.tenantId(),
                request.projectId(), enabled);
    }

    @DeleteMapping("/datasets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDataset(@PathVariable long id) {
        datasetService.delete(id);
    }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    public EvalCase createCase(@Valid @RequestBody CaseRequest request) {
        EvalCase evalCase = new EvalCase(
                null,
                request.datasetId(),
                request.question(),
                request.expectedAnswer(),
                request.tenantId(),
                request.projectId(),
                request.userId(),
                request.conversationId(),
                request.tags(),
                request.difficulty(),
                request.enabled() == null || request.enabled(),
                null,
                null);
        return caseService.create(evalCase, request.toExpectedKnowledge());
    }

    @GetMapping("/datasets/{datasetId}/cases")
    public List<EvalCase> listCases(
            @PathVariable long datasetId,
            @RequestParam(defaultValue = "true") boolean onlyEnabled,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return caseService.list(datasetId, onlyEnabled, limit, offset);
    }

    @GetMapping("/cases")
    public List<EvalCase> listCasesByQuery(
            @RequestParam long datasetId,
            @RequestParam(defaultValue = "true") boolean onlyEnabled,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return caseService.list(datasetId, onlyEnabled, limit, offset);
    }

    @GetMapping("/cases/{caseId}")
    public EvalCase getCase(@PathVariable long caseId) {
        return caseService.get(caseId);
    }

    @PutMapping("/cases/{caseId}")
    public EvalCase updateCase(
            @PathVariable long caseId,
            @Valid @RequestBody CaseRequest request) {
        EvalCase current = caseService.get(caseId);
        EvalCase changes = new EvalCase(
                caseId,
                request.datasetId(),
                request.question(),
                request.expectedAnswer(),
                request.tenantId(),
                request.projectId(),
                request.userId(),
                request.conversationId(),
                request.tags(),
                request.difficulty(),
                request.enabled() == null ? current.enabled() : request.enabled(),
                current.createdAt(),
                null);
        return caseService.update(caseId, changes, request.toExpectedKnowledge());
    }

    @DeleteMapping("/cases/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCase(@PathVariable long caseId) {
        caseService.delete(caseId);
    }

    @PostMapping(value = "/cases/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvalCaseCsvImportResult importCases(
            @RequestParam long datasetId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("evaluation CSV file must not be empty");
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return csvImportService.importCsv(
                    datasetId,
                    reader,
                    new EvalCaseCsvImportService.ImportDefaults(
                            tenantId, projectId, userId, conversationId));
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to read evaluation CSV", exception);
        }
    }

    @GetMapping("/cases/{caseId}/expected-knowledge")
    public List<ExpectedKnowledge> expectedKnowledge(@PathVariable long caseId) {
        return caseService.expectedKnowledge(caseId);
    }

    @PutMapping("/cases/{caseId}/expected-knowledge")
    public void replaceExpectedKnowledge(
            @PathVariable long caseId,
            @Valid @RequestBody ExpectedKnowledgeRequest request) {
        caseService.replaceExpectedKnowledge(caseId, request.toExpectedKnowledge(caseId));
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public EvalRun createRun(@Valid @RequestBody RunRequest request) {
        return runService.createRun(new CreateEvalRunCommand(
                request.datasetId(),
                request.runName(),
                enumValue(EvalRunType.class, request.runType(), EvalRunType.MANUAL),
                enumValue(BeforeAfterGroup.class, request.beforeAfterGroup(), BeforeAfterGroup.NORMAL),
                request.engineConfigSnapshot(),
                request.modelConfigSnapshot(),
                request.createdBy(),
                request.async() == null || request.async()));
    }

    @GetMapping("/runs")
    public List<EvalRun> listRuns(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return repository.listRuns(datasetId, limit, offset);
    }

    @GetMapping("/runs/{runId}")
    public EvalRun getRun(@PathVariable long runId) {
        return repository.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("eval run not found: " + runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public void cancelRun(@PathVariable long runId) {
        runService.cancel(runId);
    }

    @GetMapping("/runs/{runId}/results")
    public List<EvalResult> listResults(
            @PathVariable long runId,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return repository.listResults(runId, limit, offset);
    }

    @GetMapping("/results/{resultId}/judge-details")
    public List<EvalJudgeDetail> listJudgeDetails(@PathVariable long resultId) {
        return repository.listJudgeDetails(resultId);
    }

    @GetMapping("/runs/{runId}/report")
    public EvalReport getReport(@PathVariable long runId) {
        return repository.findReportByRunId(runId).orElseGet(() -> reportService.buildReport(runId));
    }

    @PostMapping("/compare")
    public EvalCompareReport compare(@Valid @RequestBody CompareRequest request) {
        return reportService.compare(request.beforeRunId(), request.afterRunId());
    }

    @PostMapping("/governance/snapshot")
    public EvalGovernanceSnapshot captureGovernanceSnapshot(
            @Valid @RequestBody GovernanceSnapshotRequest request) {
        return governanceService.capture(request.tenantId(), request.projectId(), request.snapshotDate());
    }

    @GetMapping("/governance/summary")
    public EvalGovernanceSnapshot governanceSummary(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId) {
        return governanceService.latest(tenantId, projectId);
    }

    @GetMapping("/governance/trend")
    public List<EvalGovernanceSnapshot> governanceTrend(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return governanceService.trend(tenantId, projectId, from, to);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        return value == null || value.isBlank()
                ? fallback
                : Enum.valueOf(type, value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public record DatasetRequest(
            @NotBlank String name,
            String description,
            String tenantId,
            String projectId,
            String createdBy,
            Boolean enabled) {
    }

    public record CaseRequest(
            @NotNull Long datasetId,
            @NotBlank String question,
            String expectedAnswer,
            String tenantId,
            String projectId,
            String userId,
            String conversationId,
            List<String> tags,
            String difficulty,
            Boolean enabled,
            List<ExpectedKnowledgeBody> expectedKnowledge) {

        List<ExpectedKnowledge> toExpectedKnowledge() {
            if (expectedKnowledge == null) {
                return List.of();
            }
            return expectedKnowledge.stream()
                    .map(item -> new ExpectedKnowledge(
                            null, null, item.knowledgeId(), item.relevanceGrade(), null))
                    .toList();
        }
    }

    public record ExpectedKnowledgeRequest(List<ExpectedKnowledgeBody> expectedKnowledge) {

        List<ExpectedKnowledge> toExpectedKnowledge(long caseId) {
            if (expectedKnowledge == null) {
                return List.of();
            }
            return expectedKnowledge.stream()
                    .map(item -> new ExpectedKnowledge(
                            null, caseId, item.knowledgeId(), item.relevanceGrade(), null))
                    .toList();
        }
    }

    public record ExpectedKnowledgeBody(@NotNull Long knowledgeId, int relevanceGrade) {
    }

    public record RunRequest(
            @NotNull Long datasetId,
            String runName,
            String runType,
            String beforeAfterGroup,
            String engineConfigSnapshot,
            String modelConfigSnapshot,
            String createdBy,
            Boolean async) {
    }

    public record CompareRequest(@NotNull Long beforeRunId, @NotNull Long afterRunId) {
    }

    public record GovernanceSnapshotRequest(
            String tenantId,
            String projectId,
            LocalDate snapshotDate) {
    }
}
