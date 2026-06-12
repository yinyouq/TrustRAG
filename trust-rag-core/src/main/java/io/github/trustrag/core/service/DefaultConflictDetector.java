package io.github.trustrag.core.service;

import io.github.trustrag.core.config.ConflictDetectionOptions;
import io.github.trustrag.core.model.ConflictCheckResult;
import io.github.trustrag.core.model.ConflictRecord;
import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.ConflictDetector;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import io.github.trustrag.core.spi.KnowledgeRelationJudge;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeVectorStore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DefaultConflictDetector implements ConflictDetector {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeVectorStore vectorStore;
    private final ConflictRecordRepository conflictRepository;
    private final KnowledgeRelationJudge relationJudge;
    private final ConflictDetectionOptions options;
    private final Clock clock;

    public DefaultConflictDetector(
            KnowledgeRepository knowledgeRepository,
            KnowledgeVectorStore vectorStore,
            ConflictRecordRepository conflictRepository,
            KnowledgeRelationJudge relationJudge,
            ConflictDetectionOptions options,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorStore = vectorStore;
        this.conflictRepository = conflictRepository;
        this.relationJudge = relationJudge;
        this.options = options;
        this.clock = clock;
    }

    @Override
    public ConflictCheckResult check(KnowledgeItem candidate, List<Float> candidateVector) {
        if (!options.enabled()) {
            return ConflictCheckResult.none();
        }
        ScopeContext scope = new ScopeContext(
                candidate.userId(), candidate.conversationId(), candidate.projectId(), candidate.tenantId());
        List<VectorHit> hits = new ArrayList<>();
        if (options.compareWithHigh()) {
            hits.addAll(search(candidateVector, scope, TrustLevel.HIGH, Set.of(KnowledgeStatus.HIGH_ENABLED)));
        }
        if (options.compareWithMedium()) {
            hits.addAll(search(
                    candidateVector, scope, TrustLevel.MEDIUM,
                    Set.of(KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING)));
        }

        List<ConflictRecord> records = new ArrayList<>();
        double risk = 0.0;
        for (KnowledgeItem existing : knowledgeRepository.findAllByIds(
                hits.stream().map(VectorHit::knowledgeId).toList())) {
            if (existing.id().equals(candidate.id())) {
                continue;
            }
            KnowledgeRelationJudge.RelationJudgement judgement = relationJudge.judge(candidate, existing);
            if (judgement.type() == ConflictType.UNRELATED) {
                continue;
            }
            ConflictRecord record = conflictRepository.save(new ConflictRecord(
                    null, candidate.id(), existing.id(), judgement.type(),
                    judgement.confidence(), judgement.reason(), clock.instant()));
            records.add(record);
            if (judgement.type() == ConflictType.CONFLICT) {
                risk = Math.max(risk, judgement.confidence());
            } else if (judgement.type() == ConflictType.VERSION_DIFF) {
                risk = Math.max(risk, judgement.confidence() * 0.35);
            }
        }
        return new ConflictCheckResult(
                Math.min(risk, 1.0), records,
                records.isEmpty() ? "" : "Compared with similar high/medium knowledge");
    }

    private List<VectorHit> search(
            List<Float> vector,
            ScopeContext scope,
            TrustLevel trustLevel,
            Set<KnowledgeStatus> statuses) {
        return vectorStore.search(new VectorSearchRequest(
                vector, scope, Set.of(trustLevel), statuses,
                options.topK(), options.similarityThreshold()));
    }
}
