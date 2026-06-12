package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeLineage;

import java.util.List;

public interface KnowledgeLineageRepository {

    KnowledgeLineage save(KnowledgeLineage lineage);

    List<KnowledgeLineage> findByKnowledgeId(long knowledgeId);
}
