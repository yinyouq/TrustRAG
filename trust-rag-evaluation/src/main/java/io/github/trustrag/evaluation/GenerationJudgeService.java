package io.github.trustrag.evaluation;

import io.github.trustrag.core.model.RagAnswer;

public interface GenerationJudgeService {

    JudgedGeneration judge(EvalRun run, EvalCase evalCase, RagAnswer answer);
}
