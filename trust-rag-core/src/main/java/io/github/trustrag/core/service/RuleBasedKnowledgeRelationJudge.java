package io.github.trustrag.core.service;

import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeRelationJudge;
import io.github.trustrag.core.util.ClaimNormalizer;

import java.util.List;

public final class RuleBasedKnowledgeRelationJudge implements KnowledgeRelationJudge {

    private static final List<String> NEGATIONS = List.of(
            "不支持", "不能", "禁止", "无法", "not supported", "cannot", "must not");

    @Override
    public RelationJudgement judge(KnowledgeItem candidate, KnowledgeItem existing) {
        String candidateVersion = candidate.governance().applicableVersion();
        String existingVersion = existing.governance().applicableVersion();
        if (hasText(candidateVersion)
                && hasText(existingVersion)
                && !candidateVersion.equalsIgnoreCase(existingVersion)) {
            return new RelationJudgement(
                    ConflictType.VERSION_DIFF, 0.75, "Applicable versions differ");
        }

        String left = ClaimNormalizer.normalize(effectiveClaim(candidate));
        String right = ClaimNormalizer.normalize(effectiveClaim(existing));
        if (left.equals(right)) {
            return new RelationJudgement(ConflictType.SUPPORT, 0.95, "Normalized claims match");
        }
        boolean leftNegative = containsAny(effectiveClaim(candidate), NEGATIONS);
        boolean rightNegative = containsAny(effectiveClaim(existing), NEGATIONS);
        if (leftNegative != rightNegative) {
            return new RelationJudgement(
                    ConflictType.CONFLICT, 0.80, "Claims contain opposing polarity");
        }
        return new RelationJudgement(
                ConflictType.UNRELATED, 0.30, "No deterministic conflict signal");
    }

    private String effectiveClaim(KnowledgeItem item) {
        return item.claim() == null || item.claim().isBlank() ? item.content() : item.claim();
    }

    private boolean containsAny(String value, List<String> tokens) {
        String normalized = value == null ? "" : value.toLowerCase();
        return tokens.stream().anyMatch(normalized::contains);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
