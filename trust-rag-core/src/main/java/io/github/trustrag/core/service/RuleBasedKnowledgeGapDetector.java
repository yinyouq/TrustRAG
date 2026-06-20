package io.github.trustrag.core.service;

import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.KnowledgeGapType;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.spi.KnowledgeGapDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
