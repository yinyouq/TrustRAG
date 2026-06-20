package io.github.trustrag.core.service;

import io.github.trustrag.core.model.HybridCandidate;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.spi.RrfFusionService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultRrfFusionService implements RrfFusionService {

    @Override
    public List<HybridCandidate> fuse(
            List<VectorHit> vectorHits,
            List<KeywordHit> keywordHits,
            int rrfK) {
        Map<Long, MutableCandidate> candidates = new LinkedHashMap<>();
        for (int index = 0; index < vectorHits.size(); index++) {
            VectorHit hit = vectorHits.get(index);
            MutableCandidate candidate = candidates.computeIfAbsent(
                    hit.knowledgeId(), MutableCandidate::new);
            candidate.vectorScore = hit.vectorScore();
            candidate.vectorRank = index + 1;
        }
        for (int index = 0; index < keywordHits.size(); index++) {
            KeywordHit hit = keywordHits.get(index);
            MutableCandidate candidate = candidates.computeIfAbsent(
                    hit.knowledgeId(), MutableCandidate::new);
            candidate.keywordScore = hit.keywordScore();
            candidate.keywordRank = index + 1;
        }
        return candidates.values().stream()
                .map(candidate -> candidate.toCandidate(rrfK))
                .sorted(Comparator
                        .comparingDouble(HybridCandidate::rrfScore)
                        .reversed()
                        .thenComparingLong(HybridCandidate::knowledgeId))
                .toList();
    }

    private static final class MutableCandidate {
        private final long knowledgeId;
        private Double vectorScore;
        private Integer vectorRank;
        private Double keywordScore;
        private Integer keywordRank;

        private MutableCandidate(long knowledgeId) {
            this.knowledgeId = knowledgeId;
        }

        private HybridCandidate toCandidate(int rrfK) {
            double score = 0.0;
            if (vectorRank != null) {
                score += 1.0 / (rrfK + vectorRank);
            }
            if (keywordRank != null) {
                score += 1.0 / (rrfK + keywordRank);
            }
            return new HybridCandidate(
                    knowledgeId, vectorScore, vectorRank,
                    keywordScore, keywordRank, score);
        }
    }
}
