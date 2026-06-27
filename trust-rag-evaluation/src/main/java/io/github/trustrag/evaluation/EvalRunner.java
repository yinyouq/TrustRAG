package io.github.trustrag.evaluation;

/**
 * EvalRunner 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface EvalRunner {

    void run(long evalRunId);
}
