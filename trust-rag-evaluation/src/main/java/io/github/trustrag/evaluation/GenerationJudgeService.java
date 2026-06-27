package io.github.trustrag.evaluation;

import io.github.trustrag.core.model.RagAnswer;

/**
 * GenerationJudgeService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public interface GenerationJudgeService {

    JudgedGeneration judge(EvalRun run, EvalCase evalCase, RagAnswer answer);
}
