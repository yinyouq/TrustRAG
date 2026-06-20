package io.github.trustrag.evaluation;

import java.time.Instant;

public record EvalResult(
        Long id,
        Long evalRunId,
        Long evalCaseId,
        String traceId,
        String question,
        String expectedAnswer,
        String answer,
        int retrievedCount,
        int expectedKnowledgeCount,
        Double recallAt5,
        Double recallAt10,
        Double precisionAt5,
        Double precisionAt10,
        Double mrr,
        Double ndcgAt5,
        Double ndcgAt10,
        Double faithfulness,
        Double answerCorrectness,
        Double answerRelevance,
        Double hallucinationScore,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        EvalResultStatus status,
        String errorMessage,
        Instant createdAt) {

    public EvalResult withId(Long value) {
        return new EvalResult(
                value, evalRunId, evalCaseId, traceId, question, expectedAnswer, answer,
                retrievedCount, expectedKnowledgeCount, recallAt5, recallAt10, precisionAt5,
                precisionAt10, mrr, ndcgAt5, ndcgAt10, faithfulness, answerCorrectness,
                answerRelevance, hallucinationScore, promptTokens, completionTokens, latencyMs,
                status, errorMessage, createdAt);
    }
}
