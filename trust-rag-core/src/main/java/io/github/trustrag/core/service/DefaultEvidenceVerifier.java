package io.github.trustrag.core.service;

import io.github.trustrag.core.config.SourceScoreOptions;
import io.github.trustrag.core.model.EvidenceVerificationResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.EvidenceVerifier;

/**
 * DefaultEvidenceVerifier 承载 trust-rag-core 模块中的领域逻辑或基础设施适配职责。
 */
public final class DefaultEvidenceVerifier implements EvidenceVerifier {

    private final SourceScoreOptions sourceScores;

    public DefaultEvidenceVerifier(SourceScoreOptions sourceScores) {
        this.sourceScores = sourceScores;
    }

    @Override
    public EvidenceVerificationResult verify(KnowledgeItem candidate) {
        double sourceScore = sourceScores.score(candidate.sourceType());
        double evidenceScore = evidenceScore(candidate);
        boolean sufficient = sourceScore >= 0.60 && evidenceScore >= 0.50;
        String reason = sufficient
                ? "Source and evidence satisfy automatic promotion requirements"
                : "Source or evidence is insufficient for medium trust";
        return new EvidenceVerificationResult(sourceScore, evidenceScore, sufficient, reason);
    }

    private double evidenceScore(KnowledgeItem candidate) {
        if (candidate.evidence() == null || candidate.evidence().isBlank()) {
            return 0.0;
        }
        double score = 0.50;
        if (candidate.sourceRef() != null && !candidate.sourceRef().isBlank()) {
            score += 0.20;
        }
        if (candidate.evidence().length() >= 40) {
            score += 0.15;
        }
        if (candidate.governance().sourceTime() != null) {
            score += 0.15;
        }
        return Math.min(score, 1.0);
    }
}
