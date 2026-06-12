package io.github.trustrag.core.model;

public record EvidenceVerificationResult(
        double sourceScore,
        double evidenceScore,
        boolean sufficient,
        String reason) {
}
