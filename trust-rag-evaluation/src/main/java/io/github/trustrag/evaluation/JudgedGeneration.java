package io.github.trustrag.evaluation;

import java.util.List;

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
