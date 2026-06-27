package io.github.trustrag.core.service;

import io.github.trustrag.core.model.HybridCandidate;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.spi.RrfFusionService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 融合实现。
 *
 * <p>RRF 只依赖各检索分支的排序名次，不要求向量分数和 BM25 分数处在同一量纲，
 * 因此适合作为 TrustRAG 的混合检索默认融合策略。</p>
 */
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
                // 排名越靠前贡献越大，rrfK 用来平滑不同检索分支的头部差异。
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
