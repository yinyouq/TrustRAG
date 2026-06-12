package io.github.trustrag.core.model;

public record LlmResponse(String text, Double confidence, TokenUsage tokenUsage) {

    public LlmResponse {
        tokenUsage = tokenUsage == null ? TokenUsage.unknown() : tokenUsage;
    }
}
