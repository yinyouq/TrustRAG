package io.github.trustrag.core.config;

public record RetrievalOptions(
        int highTrustTopK,
        int lowTrustTopK,
        double minVectorScore,
        double highTrustWeight,
        double lowConversationWeight,
        double lowUserWeight,
        double lowProjectWeight,
        double lowTenantWeight) {

    public RetrievalOptions {
        if (highTrustTopK < 0 || lowTrustTopK < 0) {
            throw new IllegalArgumentException("Retrieval TopK cannot be negative");
        }
        if (minVectorScore < 0.0 || minVectorScore > 1.0) {
            throw new IllegalArgumentException("minVectorScore must be between 0 and 1");
        }
    }
}
