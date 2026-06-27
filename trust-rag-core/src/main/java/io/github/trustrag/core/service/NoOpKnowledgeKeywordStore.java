package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;

import java.util.List;

/**
 * NoOpKnowledgeKeywordStore 承载 trust-rag-core 模块中的领域逻辑或基础设施适配职责。
 */
public final class NoOpKnowledgeKeywordStore implements KnowledgeKeywordStore {

    @Override
    public void initialize() {
    }

    @Override
    public void upsert(KnowledgeItem knowledge) {
    }

    @Override
    public void delete(long knowledgeId) {
    }

    @Override
    public List<KeywordHit> search(KeywordSearchRequest request) {
        return List.of();
    }

    @Override
    public boolean available() {
        return false;
    }
}
