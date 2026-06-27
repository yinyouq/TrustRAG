package io.github.trustrag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.service.TrustRagEngine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证 DefaultEvalRunner 的关键行为、边界条件和回归场景。
 */
class DefaultEvalRunnerTest {

    @Test
    void marksRunFailedWhenCasesCannotBeLoaded() {
        InMemoryEvaluationRepository repository = new InMemoryEvaluationRepository() {
            @Override
            public List<EvalCase> listCases(long datasetId, boolean onlyEnabled, int limit, int offset) {
                throw new IllegalStateException("case storage unavailable");
            }
        };
        EvalRun run = repository.createRun(pendingRun());

        assertThatCode(() -> runner(repository).run(run.id())).doesNotThrowAnyException();

        EvalRun completed = repository.findRun(run.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(EvalRunStatus.FAILED);
        assertThat(completed.errorMessage()).contains("case storage unavailable");
        assertThat(completed.finishedAt()).isNotNull();
    }

    @Test
    void marksRunFailedWhenReportGenerationFails() {
        InMemoryEvaluationRepository repository = new InMemoryEvaluationRepository() {
            @Override
            public List<EvalResult> listResults(long runId, int limit, int offset) {
                throw new IllegalStateException("report storage unavailable");
            }
        };
        EvalRun run = repository.createRun(pendingRun());

        assertThatCode(() -> runner(repository).run(run.id())).doesNotThrowAnyException();

        EvalRun completed = repository.findRun(run.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(EvalRunStatus.FAILED);
        assertThat(completed.errorMessage()).contains("report storage unavailable");
    }

    private DefaultEvalRunner runner(EvaluationRepository repository) {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);
        EvalReportService reportService = new EvalReportService(repository, new ObjectMapper(), clock);
        TrustRagEngine engine = request -> (RagAnswer) null;
        return new DefaultEvalRunner(
                repository,
                new EvalTraceReader() {
                    @Override
                    public List<Long> findRetrievedKnowledgeIds(String traceId) {
                        return List.of();
                    }

                    @Override
                    public List<Long> findUsedKnowledgeIds(String traceId) {
                        return List.of();
                    }
                },
                engine,
                new RetrievalMetricCalculator(),
                new NoOpGenerationJudgeService(),
                reportService,
                clock);
    }

    private EvalRun pendingRun() {
        return new EvalRun(
                null, 2L, "run", EvalRunType.MANUAL, BeforeAfterGroup.NORMAL,
                EvalRunStatus.PENDING, 0, 0, 0, null, null, Instant.EPOCH,
                null, null, null, "tester", Instant.EPOCH);
    }
}
