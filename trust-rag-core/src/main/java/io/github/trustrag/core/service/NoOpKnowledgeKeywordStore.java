package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;

import java.util.List;

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
