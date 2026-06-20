package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

public interface KnowledgeIndexService {

    void upsert(KnowledgeItem knowledge, List<Float> vector);

    void delete(long knowledgeId);
}
