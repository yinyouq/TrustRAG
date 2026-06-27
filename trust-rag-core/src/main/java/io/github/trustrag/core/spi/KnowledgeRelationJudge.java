package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;

/**
 * KnowledgeRelationJudge 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface KnowledgeRelationJudge {

    RelationJudgement judge(KnowledgeItem candidate, KnowledgeItem existing);

    record RelationJudgement(ConflictType type, double confidence, String reason) {
    }
}
