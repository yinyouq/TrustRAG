package io.github.trustrag.evaluation;

import java.time.Instant;

/**
 * EvalJudgeDetail 保存明细信息，帮助管理台展示诊断上下文。
 */
public record EvalJudgeDetail(
        Long id,
        Long evalResultId,
        Long evalRunId,
        Long evalCaseId,
        JudgeType judgeType,
        String model,
        String prompt,
        String rawOutput,
        Double score,
        Boolean passed,
        String reason,
        Instant createdAt) {

    public EvalJudgeDetail withResultId(Long value) {
        return new EvalJudgeDetail(
                id, value, evalRunId, evalCaseId, judgeType, model, prompt,
                rawOutput, score, passed, reason, createdAt);
    }

    public EvalJudgeDetail withId(Long value) {
        return new EvalJudgeDetail(
                value, evalResultId, evalRunId, evalCaseId, judgeType, model, prompt,
                rawOutput, score, passed, reason, createdAt);
    }
}
