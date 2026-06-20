package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class TestKnowledgeRepository implements KnowledgeRepository {

    private final Map<Long, KnowledgeItem> items = new LinkedHashMap<>();
    private final Map<Long, Integer> usageIncrements = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public KnowledgeItem save(KnowledgeItem knowledge) {
        KnowledgeItem saved = knowledge.id() == null
                ? knowledge.withId(ids.incrementAndGet())
                : knowledge;
        items.put(saved.id(), saved);
        return saved;
    }

    @Override
    public void update(KnowledgeItem knowledge) {
        items.put(knowledge.id(), knowledge);
    }

    @Override
    public boolean updateIfState(
            KnowledgeItem knowledge,
            KnowledgeStatus expectedStatus,
            int expectedVersion) {
        KnowledgeItem current = items.get(knowledge.id());
        if (current == null
                || current.status() != expectedStatus
                || current.version() != expectedVersion) {
            return false;
        }
        items.put(knowledge.id(), knowledge);
        return true;
    }

    @Override
    public Optional<KnowledgeItem> findById(long id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public Optional<KnowledgeItem> findByHash(String hash) {
        return items.values().stream().filter(item -> hash.equals(item.hash())).findFirst();
    }

    @Override
    public Optional<KnowledgeItem> findByClaimHash(String claimHash, long excludedKnowledgeId) {
        return items.values().stream()
                .filter(item -> item.id() != excludedKnowledgeId)
                .filter(item -> claimHash.equals(item.governance().claimHash()))
                .findFirst();
    }

    @Override
    public List<KnowledgeItem> findAllByIds(Collection<Long> ids) {
        return ids.stream().map(items::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<KnowledgeItem> findCandidates(
            KnowledgeStatus status,
            TrustLevel trustLevel,
            ScopeType scopeType,
            int limit,
            int offset) {
        return items.values().stream()
                .filter(item -> item.status() == status)
                .filter(item -> trustLevel == null || item.trustLevel() == trustLevel)
                .filter(item -> scopeType == null || item.scopeType() == scopeType)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<KnowledgeItem> findByTrustAndStatuses(
            TrustLevel trustLevel,
            Set<KnowledgeStatus> statuses,
            int limit,
            int offset) {
        return items.values().stream()
                .filter(item -> item.trustLevel() == trustLevel)
                .filter(item -> statuses.contains(item.status()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<KnowledgeItem> findPromotionCandidates(int limit) {
        return items.values().stream()
                .filter(item -> item.status().isPromotionCandidate())
                .limit(limit)
                .toList();
    }

    @Override
    public List<KnowledgeItem> findExpired(Instant now, int limit) {
        return items.values().stream()
                .filter(item -> item.status().isRetrievable())
                .filter(item -> (item.expiresAt() != null && item.expiresAt().isBefore(now))
                        || (item.governance().validTo() != null
                        && item.governance().validTo().isBefore(now)))
                .limit(limit)
                .toList();
    }

    @Override
    public List<KnowledgeItem> findNegativeFeedbackCandidates(int threshold, int limit) {
        return List.of();
    }

    @Override
    public void incrementUsageCount(long knowledgeId) {
        usageIncrements.merge(knowledgeId, 1, Integer::sum);
    }

    @Override
    public void incrementFeedbackCounts(
            Collection<Long> knowledgeIds,
            boolean positive,
            boolean negative) {
    }

    int usageIncrements(long knowledgeId) {
        return usageIncrements.getOrDefault(knowledgeId, 0);
    }
}
