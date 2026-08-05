package io.github.trustrag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportServiceTest {

    @Test
    void separatesRagAndJudgeLatencyAcrossAllPercentiles() {
        InMemoryEvaluationRepository repository = new InMemoryEvaluationRepository();
        EvalRun run = repository.createRun(new EvalRun(
                null, 1L, "latency", EvalRunType.MANUAL, BeforeAfterGroup.NORMAL,
                EvalRunStatus.RUNNING, 3, 0, 0, null, null, Instant.EPOCH,
                Instant.EPOCH, null, null, "tester", Instant.EPOCH));
        saveResultWithJudge(repository, run.id(), 1L, 100, 10);
        saveResultWithJudge(repository, run.id(), 2L, 200, 20);
        saveResultWithJudge(repository, run.id(), 3L, 300, 30);

        EvalReport report = new EvalReportService(
                repository, new ObjectMapper(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
                .buildReport(run.id());

        assertThat(report.avgLatencyMs()).isEqualTo(200.0);
        assertThat(report.avgLatencyWithJudgeMs()).isEqualTo(220.0);
        assertThat(report.p90LatencyMs()).isEqualTo(300.0);
        assertThat(report.p95LatencyMs()).isEqualTo(300.0);
        assertThat(report.p99LatencyMs()).isEqualTo(300.0);
        assertThat(report.p90LatencyWithJudgeMs()).isEqualTo(330.0);
        assertThat(report.p95LatencyWithJudgeMs()).isEqualTo(330.0);
        assertThat(report.p99LatencyWithJudgeMs()).isEqualTo(330.0);
    }

    private void saveResultWithJudge(
            InMemoryEvaluationRepository repository,
            long runId,
            long caseId,
            long ragLatencyMs,
            long judgeLatencyMs) {
        EvalResult result = repository.saveResult(new EvalResult(
                null, runId, caseId, null, "question", null, "answer",
                0, 0, null, null, null, null, null, null, null, null, null,
                null, null, 0, 0, ragLatencyMs, EvalResultStatus.SUCCEEDED,
                null, Instant.EPOCH));
        repository.saveJudgeDetail(new EvalJudgeDetail(
                null, result.id(), runId, caseId, JudgeType.FAITHFULNESS, "judge",
                null, null, 1.0, true, null, judgeLatencyMs, 0, Instant.EPOCH));
    }
}
