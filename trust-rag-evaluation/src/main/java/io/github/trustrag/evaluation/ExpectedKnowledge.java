package io.github.trustrag.evaluation;

import java.time.Instant;

public record ExpectedKnowledge(
        Long id,
        Long evalCaseId,
        Long knowledgeId,
        int relevanceGrade,
        Instant createdAt) {

    public ExpectedKnowledge {
        if (relevanceGrade < 1) {
            relevanceGrade = 1;
        }
    }

    public ExpectedKnowledge withId(Long value) {
        return new ExpectedKnowledge(value, evalCaseId, knowledgeId, relevanceGrade, createdAt);
    }
}
