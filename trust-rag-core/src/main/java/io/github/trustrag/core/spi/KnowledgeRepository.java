package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Set;

public interface KnowledgeRepository {

    KnowledgeItem save(KnowledgeItem knowledge);

    void update(KnowledgeItem knowledge);

    boolean updateIfState(
            KnowledgeItem knowledge,
            KnowledgeStatus expectedStatus,
            int expectedVersion);

    Optional<KnowledgeItem> findById(long id);

    Optional<KnowledgeItem> findByHash(String hash);

    Optional<KnowledgeItem> findByClaimHash(String claimHash, long excludedKnowledgeId);

    List<KnowledgeItem> findAllByIds(Collection<Long> ids);

    List<KnowledgeItem> findCandidates(
            KnowledgeStatus status,
            TrustLevel trustLevel,
            ScopeType scopeType,
            int limit,
            int offset);

    List<KnowledgeItem> findByTrustAndStatuses(
            TrustLevel trustLevel,
            Set<KnowledgeStatus> statuses,
            int limit,
            int offset);

    List<KnowledgeItem> findPromotionCandidates(int limit);

    List<KnowledgeItem> findExpired(Instant now, int limit);

    List<KnowledgeItem> findNegativeFeedbackCandidates(int threshold, int limit);

    void incrementUsageCount(long knowledgeId);

    void incrementFeedbackCounts(Collection<Long> knowledgeIds, boolean positive, boolean negative);
}
