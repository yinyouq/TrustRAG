package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {

    KnowledgeItem save(KnowledgeItem knowledge);

    void update(KnowledgeItem knowledge);

    boolean updateIfState(
            KnowledgeItem knowledge,
            KnowledgeStatus expectedStatus,
            int expectedVersion);

    Optional<KnowledgeItem> findById(long id);

    Optional<KnowledgeItem> findByHash(String hash);

    List<KnowledgeItem> findAllByIds(Collection<Long> ids);

    List<KnowledgeItem> findCandidates(
            KnowledgeStatus status,
            TrustLevel trustLevel,
            ScopeType scopeType,
            int limit,
            int offset);
}
