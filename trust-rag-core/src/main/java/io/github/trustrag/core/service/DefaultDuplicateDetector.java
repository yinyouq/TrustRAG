package io.github.trustrag.core.service;

import io.github.trustrag.core.config.DuplicateDetectionOptions;
import io.github.trustrag.core.model.DuplicateAction;
import io.github.trustrag.core.model.DuplicateCheckResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.DuplicateDetector;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.util.ClaimNormalizer;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class DefaultDuplicateDetector implements DuplicateDetector {

    private static final Set<KnowledgeStatus> HIGH_STATUSES = Set.of(KnowledgeStatus.HIGH_ENABLED);
    private static final Set<KnowledgeStatus> MEDIUM_STATUSES = Set.of(
            KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeVectorStore vectorStore;
    private final DuplicateDetectionOptions options;

    public DefaultDuplicateDetector(
            KnowledgeRepository knowledgeRepository,
            KnowledgeVectorStore vectorStore,
            DuplicateDetectionOptions options) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorStore = vectorStore;
        this.options = options;
    }

    @Override
    public DuplicateCheckResult check(KnowledgeItem candidate, List<Float> candidateVector) {
        if (!options.enabled()) {
            return DuplicateCheckResult.none();
        }
        String claimHash = ClaimNormalizer.hash(effectiveClaim(candidate));
        if (options.hashEnabled()) {
            var exact = knowledgeRepository.findByClaimHash(claimHash, candidate.id());
            if (exact.isPresent()) {
                return new DuplicateCheckResult(
                        DuplicateAction.REUSE_EXISTING, exact.get().id(), 1.0,
                        "Normalized claim already exists");
            }
        }
        if (!options.vectorEnabled()) {
            return DuplicateCheckResult.none();
        }
        ScopeContext scope = new ScopeContext(
                candidate.userId(), candidate.conversationId(), candidate.projectId(), candidate.tenantId());
        return List.of(
                        search(candidateVector, scope, TrustLevel.HIGH, HIGH_STATUSES),
                        search(candidateVector, scope, TrustLevel.MEDIUM, MEDIUM_STATUSES))
                .stream()
                .flatMap(List::stream)
                .filter(hit -> hit.knowledgeId() != candidate.id())
                .max(Comparator.comparingDouble(VectorHit::vectorScore))
                .filter(hit -> hit.vectorScore() >= options.similarityThreshold())
                .map(hit -> new DuplicateCheckResult(
                        DuplicateAction.MERGE_PENDING, hit.knowledgeId(), hit.vectorScore(),
                        "Semantic similarity exceeds merge threshold"))
                .orElseGet(DuplicateCheckResult::none);
    }

    private List<VectorHit> search(
            List<Float> vector,
            ScopeContext scope,
            TrustLevel trustLevel,
            Set<KnowledgeStatus> statuses) {
        return vectorStore.search(new VectorSearchRequest(
                vector, scope, Set.of(trustLevel), statuses, 3, options.similarityThreshold()));
    }

    private String effectiveClaim(KnowledgeItem item) {
        return item.claim() == null || item.claim().isBlank() ? item.content() : item.claim();
    }
}
