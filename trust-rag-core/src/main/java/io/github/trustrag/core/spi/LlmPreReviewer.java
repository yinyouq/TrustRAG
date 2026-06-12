package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;

import java.util.List;

public interface LlmPreReviewer {

    PreReviewResult review(KnowledgeItem candidate, List<KnowledgeItem> similarKnowledge);
}
