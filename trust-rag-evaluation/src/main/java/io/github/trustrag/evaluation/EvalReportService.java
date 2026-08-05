package io.github.trustrag.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 评估报告服务。
 *
 * <p>把逐题结果聚合为运行级报告，并支持 Before/After 差值对比。</p>
 */
public final class EvalReportService {

    private final EvaluationRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvalReportService(EvaluationRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public EvalReport buildReport(long runId) {
        EvalRun run = repository.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("eval run not found: " + runId));
        List<EvalResult> results = repository.listResults(runId, Integer.MAX_VALUE, 0);
        int success = (int) results.stream().filter(result -> result.status() == EvalResultStatus.SUCCEEDED).count();
        int failed = results.size() - success;
        List<Long> ragLatencies = successfulLatencies(results);
        List<Long> endToEndLatencies = latenciesWithJudge(results);
        EvalReport report = new EvalReport(
                null,
                runId,
                run.datasetId(),
                results.size(),
                success,
                failed,
                average(results.stream().map(EvalResult::recallAt5).toList()),
                average(results.stream().map(EvalResult::recallAt10).toList()),
                average(results.stream().map(EvalResult::precisionAt5).toList()),
                average(results.stream().map(EvalResult::precisionAt10).toList()),
                average(results.stream().map(EvalResult::mrr).toList()),
                average(results.stream().map(EvalResult::ndcgAt5).toList()),
                average(results.stream().map(EvalResult::ndcgAt10).toList()),
                average(results.stream().map(EvalResult::faithfulness).toList()),
                average(results.stream().map(EvalResult::answerCorrectness).toList()),
                average(results.stream().map(EvalResult::answerRelevance).toList()),
                average(results.stream().map(EvalResult::hallucinationScore).toList()),
                averageLatency(ragLatencies),
                averageLatency(endToEndLatencies),
                percentileLatency(ragLatencies, 0.90),
                percentileLatency(endToEndLatencies, 0.90),
                percentileLatency(ragLatencies, 0.95),
                percentileLatency(endToEndLatencies, 0.95),
                percentileLatency(ragLatencies, 0.99),
                percentileLatency(endToEndLatencies, 0.99),
                summary(results),
                clock.instant());
        return repository.saveReport(report);
    }

    public EvalCompareReport compare(long beforeRunId, long afterRunId) {
        EvalReport before = repository.findReportByRunId(beforeRunId)
                .orElseGet(() -> buildReport(beforeRunId));
        EvalReport after = repository.findReportByRunId(afterRunId)
                .orElseGet(() -> buildReport(afterRunId));
        // 对比报告使用已保存报告，缺失时即时重建，保证前端无需关心报告是否预先生成。
        EvalCompareReport report = new EvalCompareReport(
                null,
                beforeRunId,
                afterRunId,
                delta(before.avgRecallAt10(), after.avgRecallAt10()),
                delta(before.avgMrr(), after.avgMrr()),
                delta(before.avgFaithfulness(), after.avgFaithfulness()),
                delta(before.avgAnswerCorrectness(), after.avgAnswerCorrectness()),
                delta(before.avgHallucinationScore(), after.avgHallucinationScore()),
                conclusion(before, after),
                clock.instant());
        return repository.saveCompareReport(report);
    }

    private String conclusion(EvalReport before, EvalReport after) {
        double retrievalDelta = value(delta(before.avgRecallAt10(), after.avgRecallAt10()))
                + value(delta(before.avgMrr(), after.avgMrr()));
        double generationDelta = value(delta(before.avgFaithfulness(), after.avgFaithfulness()))
                + value(delta(before.avgAnswerCorrectness(), after.avgAnswerCorrectness()))
                - value(delta(before.avgHallucinationScore(), after.avgHallucinationScore()));
        // 幻觉率越低越好，因此对总分使用负向 delta。
        double score = retrievalDelta + generationDelta;
        if (score > 0.02) {
            return "after run improved overall";
        }
        if (score < -0.02) {
            return "after run regressed overall";
        }
        return "after run is roughly unchanged";
    }

    private String summary(List<EvalResult> results) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "resultCount", results.size(),
                    "failedCases", results.stream()
                            .filter(result -> result.status() == EvalResultStatus.FAILED)
                            .map(EvalResult::evalCaseId)
                            .toList()));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Double average(List<Double> values) {
        OptionalDouble average = values.stream()
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private List<Long> successfulLatencies(List<EvalResult> results) {
        return results.stream()
                .filter(result -> result.status() == EvalResultStatus.SUCCEEDED)
                .map(EvalResult::latencyMs)
                .toList();
    }

    private List<Long> latenciesWithJudge(List<EvalResult> results) {
        return results.stream()
                .filter(result -> result.status() == EvalResultStatus.SUCCEEDED)
                .map(result -> result.latencyMs() + judgeLatency(result))
                .toList();
    }

    private long judgeLatency(EvalResult result) {
        if (result.id() == null) {
            return 0L;
        }
        return repository.listJudgeDetails(result.id()).stream()
                .mapToLong(EvalJudgeDetail::judgeLatencyMs)
                .sum();
    }

    private Double averageLatency(List<Long> latencies) {
        OptionalDouble average = latencies.stream()
                .mapToLong(Long::longValue)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private Double percentileLatency(List<Long> source, double percentile) {
        List<Long> latencies = new ArrayList<>(source);
        if (latencies.isEmpty()) {
            return null;
        }
        latencies.sort(Comparator.naturalOrder());
        // 使用 nearest-rank 计算 p90，和大多数监控面板的展示方式一致。
        int index = (int) Math.ceil(percentile * latencies.size()) - 1;
        return (double) latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
    }

    private Double delta(Double before, Double after) {
        return before == null || after == null ? null : after - before;
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }
}
