package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;

import java.util.List;

public interface KnowledgeVectorStore {

    void initialize();

    void upsert(KnowledgeItem knowledge, List<Float> vector);

    void delete(long knowledgeId);

    List<VectorHit> search(VectorSearchRequest request);
}
