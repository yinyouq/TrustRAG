package io.github.trustrag.evaluation;

import java.util.List;

/**
 * EvaluationOptions 保存一组不可变运行选项，供核心策略在执行时读取。
 */
public record EvaluationOptions(
        boolean enabled,
        Runner runner,
        Metrics metrics,
        Judge judge,
        Governance governance) {

    public EvaluationOptions {
        runner = runner == null ? Runner.defaults() : runner;
        metrics = metrics == null ? Metrics.defaults() : metrics;
        judge = judge == null ? Judge.defaults() : judge;
        governance = governance == null ? Governance.defaults() : governance;
    }

    public record Runner(int threadPoolSize, int caseTimeoutSeconds, boolean saveEvalTrace) {
        public static Runner defaults() {
            return new Runner(4, 120, true);
        }
    }

    public record Metrics(List<Integer> recallKValues, List<Integer> precisionKValues, List<Integer> ndcgKValues) {
        public Metrics {
            recallKValues = recallKValues == null || recallKValues.isEmpty()
                    ? List.of(5, 10)
                    : List.copyOf(recallKValues);
            precisionKValues = precisionKValues == null || precisionKValues.isEmpty()
                    ? List.of(5, 10)
                    : List.copyOf(precisionKValues);
            ndcgKValues = ndcgKValues == null || ndcgKValues.isEmpty()
                    ? List.of(5, 10)
                    : List.copyOf(ndcgKValues);
        }

        public static Metrics defaults() {
            return new Metrics(List.of(5, 10), List.of(5, 10), List.of(5, 10));
        }
    }

    public record Judge(
            boolean enabled,
            String model,
            double temperature,
            int maxRetry,
            boolean savePrompt,
            boolean saveOutput) {

        public static Judge defaults() {
            return new Judge(false, "default", 0.0, 1, true, true);
        }
    }

    public record Governance(boolean snapshotEnabled, String snapshotCron) {
        public static Governance defaults() {
            return new Governance(true, "0 0 2 * * ?");
        }
    }
}
