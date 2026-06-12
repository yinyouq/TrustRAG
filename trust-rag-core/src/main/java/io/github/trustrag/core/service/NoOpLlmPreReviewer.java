package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.spi.LlmPreReviewer;

import java.util.List;

public final class NoOpLlmPreReviewer implements LlmPreReviewer {

    @Override
    public PreReviewResult review(KnowledgeItem candidate, List<KnowledgeItem> similarKnowledge) {
        return PreReviewResult.conservative(
                candidate.claim() == null ? candidate.content() : candidate.claim(),
                "LLM pre-review is disabled");
    }
}
