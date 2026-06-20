package io.github.trustrag.core.config;

public record RetrievalOptions(
        int defaultTopK,
        int highTrustTopK,
        int mediumTrustTopK,
        int lowConversationTopK,
        int lowUserTopK,
        int lowProjectTopK,
        int lowTenantTopK,
        int lowGlobalCandidateTopK,
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
        if (defaultTopK < 1) {
            throw new IllegalArgumentException("defaultTopK must be positive");
        }
        if (highTrustTopK < 0 || mediumTrustTopK < 0
                || lowConversationTopK < 0 || lowUserTopK < 0
                || lowProjectTopK < 0 || lowTenantTopK < 0
                || lowGlobalCandidateTopK < 0) {
            throw new IllegalArgumentException("Retrieval TopK cannot be negative");
        }
        if (minVectorScore < 0.0 || minVectorScore > 1.0) {
            throw new IllegalArgumentException("minVectorScore must be between 0 and 1");
        }
        validateWeight(highTrustWeight, "highTrustWeight");
        validateWeight(mediumTrustWeight, "mediumTrustWeight");
        validateWeight(lowConversationWeight, "lowConversationWeight");
        validateWeight(lowUserWeight, "lowUserWeight");
        validateWeight(lowProjectWeight, "lowProjectWeight");
        validateWeight(lowTenantWeight, "lowTenantWeight");
        validateWeight(lowGlobalCandidateWeight, "lowGlobalCandidateWeight");
    }

    public int totalConfiguredTopK() {
        return highTrustTopK + mediumTrustTopK
                + lowConversationTopK + lowUserTopK + lowProjectTopK
                + lowTenantTopK
                + (allowGlobalLowCandidate ? lowGlobalCandidateTopK : 0);
    }

    private static void validateWeight(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
