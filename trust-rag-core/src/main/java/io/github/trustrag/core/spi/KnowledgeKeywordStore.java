package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

public interface KnowledgeKeywordStore {

    void initialize();

    void upsert(KnowledgeItem knowledge);

    void delete(long knowledgeId);

    List<KeywordHit> search(KeywordSearchRequest request);

    default boolean available() {
        return true;
    }
}
