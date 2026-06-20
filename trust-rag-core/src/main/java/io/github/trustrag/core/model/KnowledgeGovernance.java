package io.github.trustrag.core.model;

import java.time.Instant;
import java.util.List;

public record KnowledgeGovernance(
        String promotionStage,
        Double promotionScore,
        String llmPreReviewResult,
        String normalizedClaim,
        String claimHash,
        Double llmScore,
        Double sourceScore,
        Double evidenceScore,
        Double feedbackScore,
        Double usageScore,
        Double generalValueScore,
        Double privacyRisk,
        Double conflictRisk,
        Double staleRisk,
        String applicableVersion,
        Instant validFrom,
        Instant validTo,
        Instant sourceTime,
        Instant lastVerifiedAt,
        int usageCount,
        int positiveFeedbackCount,
        int negativeFeedbackCount,
        List<String> tags,
        TrustLevel previousTrustLevel,
        KnowledgeStatus previousStatus) {

    public KnowledgeGovernance {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static KnowledgeGovernance empty() {
        return new KnowledgeGovernance(
                null, null, null, null, null, null, null, null, null, null, null,
                0.0, 0.0, 0.0, null, null, null, null, null,
                0, 0, 0, List.of(), null, null);
    }

    public KnowledgeGovernance withClaim(String normalized, String hash) {
        return copy(
                promotionStage, promotionScore, llmPreReviewResult, normalized, hash,
                llmScore, sourceScore, evidenceScore, feedbackScore, usageScore, generalValueScore,
                privacyRisk, conflictRisk, staleRisk, applicableVersion, validFrom, validTo,
                sourceTime, lastVerifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, previousTrustLevel, previousStatus);
    }

    public KnowledgeGovernance withPreReview(PreReviewResult result) {
        return copy(
                promotionStage, promotionScore, result.rawJson(), result.normalizedClaim(), claimHash,
                result.qualityScore(), sourceScore, evidenceScore, feedbackScore, usageScore,
                result.generalValueScore(), privacyRisk, conflictRisk, staleRisk, applicableVersion,
                validFrom, validTo, sourceTime, lastVerifiedAt, usageCount, positiveFeedbackCount,
                negativeFeedbackCount, result.tags(), previousTrustLevel, previousStatus);
    }

    public KnowledgeGovernance withEvaluation(
            String stage,
            double score,
            double source,
            double evidence,
            double feedback,
            double usage,
            double privacy,
            double conflict,
            double stale,
            Instant verifiedAt) {
        return copy(
                stage, score, llmPreReviewResult, normalizedClaim, claimHash,
                llmScore, source, evidence, feedback, usage, generalValueScore,
                privacy, conflict, stale, applicableVersion, validFrom, validTo,
                sourceTime, verifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, previousTrustLevel, previousStatus);
    }

    public KnowledgeGovernance withPrevious(TrustLevel trustLevel, KnowledgeStatus status) {
        return copy(
                promotionStage, promotionScore, llmPreReviewResult, normalizedClaim, claimHash,
                llmScore, sourceScore, evidenceScore, feedbackScore, usageScore, generalValueScore,
                privacyRisk, conflictRisk, staleRisk, applicableVersion, validFrom, validTo,
                sourceTime, lastVerifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, trustLevel, status);
    }

    public KnowledgeGovernance withStage(String stage) {
        return copy(
                stage, promotionScore, llmPreReviewResult, normalizedClaim, claimHash,
                llmScore, sourceScore, evidenceScore, feedbackScore, usageScore, generalValueScore,
                privacyRisk, conflictRisk, staleRisk, applicableVersion, validFrom, validTo,
                sourceTime, lastVerifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, previousTrustLevel, previousStatus);
    }

    public KnowledgeGovernance withPrivacyRisk(double risk) {
        return copy(
                promotionStage, promotionScore, llmPreReviewResult, normalizedClaim, claimHash,
                llmScore, sourceScore, evidenceScore, feedbackScore, usageScore, generalValueScore,
                Math.max(privacyRisk == null ? 0.0 : privacyRisk, risk),
                conflictRisk, staleRisk, applicableVersion, validFrom, validTo,
                sourceTime, lastVerifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, previousTrustLevel, previousStatus);
    }

    public KnowledgeGovernance withLifecycle(
            String version,
            Instant from,
            Instant to,
            Instant knowledgeSourceTime) {
        return copy(
                promotionStage, promotionScore, llmPreReviewResult, normalizedClaim, claimHash,
                llmScore, sourceScore, evidenceScore, feedbackScore, usageScore, generalValueScore,
                privacyRisk, conflictRisk, staleRisk, version, from, to, knowledgeSourceTime,
                lastVerifiedAt, usageCount, positiveFeedbackCount, negativeFeedbackCount,
                tags, previousTrustLevel, previousStatus);
    }

    private KnowledgeGovernance copy(
            String newPromotionStage,
            Double newPromotionScore,
            String newLlmPreReviewResult,
            String newNormalizedClaim,
            String newClaimHash,
            Double newLlmScore,
            Double newSourceScore,
            Double newEvidenceScore,
            Double newFeedbackScore,
            Double newUsageScore,
            Double newGeneralValueScore,
            Double newPrivacyRisk,
            Double newConflictRisk,
            Double newStaleRisk,
            String newApplicableVersion,
            Instant newValidFrom,
            Instant newValidTo,
            Instant newSourceTime,
            Instant newLastVerifiedAt,
            int newUsageCount,
            int newPositiveFeedbackCount,
            int newNegativeFeedbackCount,
            List<String> newTags,
            TrustLevel newPreviousTrustLevel,
            KnowledgeStatus newPreviousStatus) {
        return new KnowledgeGovernance(
                newPromotionStage, newPromotionScore, newLlmPreReviewResult,
                newNormalizedClaim, newClaimHash, newLlmScore, newSourceScore, newEvidenceScore,
                newFeedbackScore, newUsageScore, newGeneralValueScore, newPrivacyRisk,
                newConflictRisk, newStaleRisk, newApplicableVersion, newValidFrom, newValidTo,
                newSourceTime, newLastVerifiedAt, newUsageCount, newPositiveFeedbackCount,
                newNegativeFeedbackCount, newTags, newPreviousTrustLevel, newPreviousStatus);
    }
}
