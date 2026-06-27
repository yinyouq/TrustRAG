package io.github.trustrag.evaluation;

import io.github.trustrag.core.model.RagAnswer;

/**
 * NoOpGenerationJudgeService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class NoOpGenerationJudgeService implements GenerationJudgeService {

    @Override
    public JudgedGeneration judge(EvalRun run, EvalCase evalCase, RagAnswer answer) {
        return JudgedGeneration.skipped();
    }
}
