package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * KnowledgeKeywordStore 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface KnowledgeKeywordStore {

    void initialize();

    void upsert(KnowledgeItem knowledge);

    void delete(long knowledgeId);

    List<KeywordHit> search(KeywordSearchRequest request);

    default boolean available() {
        return true;
    }
}
