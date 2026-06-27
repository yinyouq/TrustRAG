package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;

import java.util.List;

/**
 * LlmPreReviewer 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface LlmPreReviewer {

    PreReviewResult review(KnowledgeItem candidate, List<KnowledgeItem> similarKnowledge);
}
