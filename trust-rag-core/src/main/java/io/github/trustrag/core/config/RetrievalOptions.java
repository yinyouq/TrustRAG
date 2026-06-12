package io.github.trustrag.core.config;

public record RetrievalOptions(
        int highTrustTopK,
        int mediumTrustTopK,
        int lowTrustTopK,
        double minVectorScore,
        double highTrustWeight,
        double mediumTrustWeight,
        double lowConversationWeight,
        double lowUserWeight,
        double lowProjectWeight,
        double lowTenantWeight,
        double lowGlobalCandidateWeight,
        boolean allowGlobalLowCandidate) {

    public RetrievalOptions {
        if (highTrustTopK < 0 || mediumTrustTopK < 0 || lowTrustTopK < 0) {
            throw new IllegalArgumentException("Retrieval TopK cannot be negative");
        }
        if (minVectorScore < 0.0 || minVectorScore > 1.0) {
            throw new IllegalArgumentException("minVectorScore must be between 0 and 1");
        }
    }
}
