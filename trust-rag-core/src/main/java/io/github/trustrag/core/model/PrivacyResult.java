package io.github.trustrag.core.model;

/**
 * PrivacyResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record PrivacyResult(boolean allowed, String sanitizedContent, double riskScore, String reason) {

    public static PrivacyResult allowed(String content) {
        return new PrivacyResult(true, content, 0.0, "");
    }
}
