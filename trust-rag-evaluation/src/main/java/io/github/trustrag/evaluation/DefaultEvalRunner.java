package io.github.trustrag.evaluation;

import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.service.TrustRagEngine;

import java.time.Clock;
import java.util.List;

public final class DefaultEvalRunner implements EvalRunner {

    private final EvaluationRepository repository;
    private final EvalTraceReader traceReader;
    private final TrustRagEngine engine;
    private final RetrievalMetricCalculator metricCalculator;
    private final GenerationJudgeService judgeService;
    private final EvalReportService reportService;
    private final Clock clock;

    public DefaultEvalRunner(
            EvaluationRepository repository,
            EvalTraceReader traceReader,
            TrustRagEngine engine,
            RetrievalMetricCalculator metricCalculator,
            GenerationJudgeService judgeService,
            EvalReportService reportService,
            Clock clock) {
        this.repository = repository;
        this.traceReader = traceReader;
        this.engine = engine;
        this.metricCalculator = metricCalculator;
        this.judgeService = judgeService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @Override
    public void run(long evalRunId) {
        EvalRun run = repository.findRun(evalRunId)
                .orElseThrow(() -> new IllegalArgumentException("eval run not found: " + evalRunId));
        repository.markRunRunning(evalRunId, clock.instant());
        int success = 0;
        int failed = 0;
        try {
            List<EvalCase> cases = repository.listCases(
                    run.datasetId(), true, Integer.MAX_VALUE, 0);
            for (EvalCase evalCase : cases) {
                EvalRun latest = repository.findRun(evalRunId).orElse(run);
                if (latest.status() == EvalRunStatus.CANCELED) {
                    repository.completeRun(
                            evalRunId, EvalRunStatus.CANCELED,
                            success, failed, clock.instant(), null);
                    return;
                }
                try {
                    runCase(run, evalCase);
                    success++;
                } catch (RuntimeException exception) {
                    saveFailure(run, evalCase, exception);
                    failed++;
                }
                repository.updateRunProgress(evalRunId, success, failed);
            }
            reportService.buildReport(evalRunId);
            EvalRunStatus status = failed == 0 ? EvalRunStatus.SUCCEEDED : EvalRunStatus.FAILED;
            repository.completeRun(evalRunId, status, success, failed, clock.instant(), null);
        } catch (RuntimeException exception) {
            repository.completeRun(
                    evalRunId,
                    EvalRunStatus.FAILED,
                    success,
                    failed,
                    clock.instant(),
                    errorMessage(exception));
        }
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void runCase(EvalRun run, EvalCase evalCase) {
        RagRequest request = RagRequest.builder()
                .question(evalCase.question())
                .tenantId(evalCase.tenantId())
                .projectId(evalCase.projectId())
                .userId(evalCase.userId())
                .conversationId(evalCase.conversationId())
                .evaluationMode(true)
                .evalRunId(run.id())
                .evalCaseId(evalCase.id())
                .enableGapDetection(false)
                .enableCandidateExtraction(false)
                .build();
        RagAnswer answer = engine.ask(request);
        List<Long> retrievedIds = traceReader.findRetrievedKnowledgeIds(answer.traceId());
        if (retrievedIds.isEmpty()) {
            retrievedIds = answer.usedKnowledge().stream().map(item -> item.knowledgeId()).toList();
        }
        List<ExpectedKnowledge> expected = repository.listExpectedKnowledge(evalCase.id());
        RetrievalMetricResult metrics = metricCalculator.calculate(retrievedIds, expected);
        JudgedGeneration judged = judgeService.judge(run, evalCase, answer);
        EvalResult saved = repository.saveResult(new EvalResult(
                null,
                run.id(),
                evalCase.id(),
                answer.traceId(),
                evalCase.question(),
                evalCase.expectedAnswer(),
                answer.answer(),
                metrics.retrievedCount(),
                metrics.expectedKnowledgeCount(),
                metrics.recallAt5(),
                metrics.recallAt10(),
                metrics.precisionAt5(),
                metrics.precisionAt10(),
                metrics.mrr(),
                metrics.ndcgAt5(),
                metrics.ndcgAt10(),
                judged.faithfulness(),
                judged.answerCorrectness(),
                judged.answerRelevance(),
                judged.hallucinationScore(),
                answer.promptTokens(),
                answer.completionTokens(),
                answer.latencyMs(),
                EvalResultStatus.SUCCEEDED,
                null,
                clock.instant()));
        for (EvalJudgeDetail detail : judged.details()) {
            repository.saveJudgeDetail(detail.withResultId(saved.id()));
        }
    }

    private void saveFailure(EvalRun run, EvalCase evalCase, RuntimeException exception) {
        repository.saveResult(new EvalResult(
                null,
                run.id(),
                evalCase.id(),
                null,
                evalCase.question(),
                evalCase.expectedAnswer(),
                null,
                0,
                repository.listExpectedKnowledge(evalCase.id()).size(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                EvalResultStatus.FAILED,
                exception.getMessage(),
                clock.instant()));
    }
}
