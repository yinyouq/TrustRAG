package io.github.trustrag.core.model;

/**
 * EvidenceVerificationResult 封装一次领域操作的结果，便于调用方读取结构化指标。
 */
public record EvidenceVerificationResult(
        double sourceScore,
        double evidenceScore,
        boolean sufficient,
        String reason) {
}
