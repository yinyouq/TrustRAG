package io.github.trustrag.core.model;

public record PrivacyResult(boolean allowed, String sanitizedContent, double riskScore, String reason) {

    public static PrivacyResult allowed(String content) {
        return new PrivacyResult(true, content, 0.0, "");
    }
}
