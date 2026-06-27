package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.spi.LlmPreReviewer;

import java.util.List;

/**
 * NoOpLlmPreReviewer 承载 trust-rag-core 模块中的领域逻辑或基础设施适配职责。
 */
public final class NoOpLlmPreReviewer implements LlmPreReviewer {

    @Override
    public PreReviewResult review(KnowledgeItem candidate, List<KnowledgeItem> similarKnowledge) {
        return PreReviewResult.conservative(
                candidate.claim() == null ? candidate.content() : candidate.claim(),
                "LLM pre-review is disabled");
    }
}
