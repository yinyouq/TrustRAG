package io.github.trustrag.evaluation;

import io.github.trustrag.core.model.RagAnswer;

public final class NoOpGenerationJudgeService implements GenerationJudgeService {

    @Override
    public JudgedGeneration judge(EvalRun run, EvalCase evalCase, RagAnswer answer) {
        return JudgedGeneration.skipped();
    }
}
