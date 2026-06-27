package io.github.trustrag.core.service;

import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.KnowledgeGapType;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.spi.KnowledgeGapDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 基于规则的知识缺口检测器。
 *
 * <p>它用检索结果、分数、上下文数量、低可信命中和答案不确定表达综合判断是否需要沉淀候选知识。</p>
 */
public final class RuleBasedKnowledgeGapDetector implements KnowledgeGapDetector {

    private final GapDetectionOptions options;

    public RuleBasedKnowledgeGapDetector(GapDetectionOptions options) {
        this.options = options;
    }

    @Override
    public GapDetectionResult detect(RagTrace trace) {
        double score = 0.0;
        List<KnowledgeGapType> types = new ArrayList<>();

        if (trace.retrievedChunks().isEmpty()) {
            score += 0.90;
            types.add(KnowledgeGapType.NO_RETRIEVAL);
        } else if (!hasKeywordSignal(trace)
                && trace.maxVectorScore() < options.lowVectorScoreThreshold()) {
            // 有关键词召回时不单看向量分数，避免混合检索场景下误报缺口。
            score += 0.30;
            types.add(KnowledgeGapType.LOW_RETRIEVAL_SCORE);
        }

        boolean hasRerankScores = trace.rerankedChunks().stream().anyMatch(chunk -> chunk.rerankScore() != null);
        if (hasRerankScores && trace.maxRerankScore() < options.lowRerankScoreThreshold()) {
            score += 0.30;
            types.add(KnowledgeGapType.LOW_RERANK_SCORE);
        }

        if (trace.noContext()) {
            score += 0.40;
            types.add(KnowledgeGapType.INSUFFICIENT_CONTEXT);
        }
        if (trace.onlyLowTrustMatched()) {
            score += 0.35;
            types.add(KnowledgeGapType.ONLY_LOW_TRUST_MATCHED);
        }
        if (containsUncertainExpression(trace.answer())) {
            score += 0.20;
            types.add(KnowledgeGapType.ANSWER_UNCERTAIN);
        }

        double normalizedScore = Math.min(score, 1.0);
        boolean hasGap = normalizedScore >= options.gapScoreThreshold();
        // 当前默认策略把“发现缺口”和“创建候选/审核任务”绑定，后续可替换 SPI 细化。
        String reason = types.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse("");
        return new GapDetectionResult(hasGap, normalizedScore, types, reason, hasGap, hasGap);
    }

    private boolean containsUncertainExpression(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        return options.uncertainExpressions().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean hasKeywordSignal(RagTrace trace) {
        return trace.retrievedChunks().stream()
                .anyMatch(chunk -> chunk.keywordRank() != null);
    }
}
