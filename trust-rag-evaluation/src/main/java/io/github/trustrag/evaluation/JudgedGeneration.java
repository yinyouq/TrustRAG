package io.github.trustrag.evaluation;

import java.util.List;

/**
 * JudgedGeneration 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record JudgedGeneration(
        Double faithfulness,
        Double answerCorrectness,
        Double answerRelevance,
        Double hallucinationScore,
        List<EvalJudgeDetail> details) {

    public JudgedGeneration {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static JudgedGeneration skipped() {
        return new JudgedGeneration(null, null, null, null, List.of());
    }
}
