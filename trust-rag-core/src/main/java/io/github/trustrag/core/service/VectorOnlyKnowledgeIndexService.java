package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeVectorStore;

import java.util.List;

/**
 * VectorOnlyKnowledgeIndexService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class VectorOnlyKnowledgeIndexService implements KnowledgeIndexService {

    private final KnowledgeVectorStore vectorStore;

    public VectorOnlyKnowledgeIndexService(KnowledgeVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void upsert(KnowledgeItem knowledge, List<Float> vector) {
        vectorStore.upsert(knowledge, vector);
    }

    @Override
    public void delete(long knowledgeId) {
        vectorStore.delete(knowledgeId);
    }
}
