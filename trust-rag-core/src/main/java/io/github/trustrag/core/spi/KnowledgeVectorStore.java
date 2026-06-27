package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;

import java.util.List;

/**
 * KnowledgeVectorStore 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface KnowledgeVectorStore {

    void initialize();

    void upsert(KnowledgeItem knowledge, List<Float> vector);

    void delete(long knowledgeId);

    List<VectorHit> search(VectorSearchRequest request);
}
