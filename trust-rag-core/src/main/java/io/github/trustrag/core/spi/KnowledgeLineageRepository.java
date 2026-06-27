package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeLineage;

import java.util.List;

/**
 * KnowledgeLineageRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface KnowledgeLineageRepository {

    KnowledgeLineage save(KnowledgeLineage lineage);

    List<KnowledgeLineage> findByKnowledgeId(long knowledgeId);
}
