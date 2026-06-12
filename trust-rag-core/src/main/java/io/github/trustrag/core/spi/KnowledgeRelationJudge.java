package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;

public interface KnowledgeRelationJudge {

    RelationJudgement judge(KnowledgeItem candidate, KnowledgeItem existing);

    record RelationJudgement(ConflictType type, double confidence, String reason) {
    }
}
