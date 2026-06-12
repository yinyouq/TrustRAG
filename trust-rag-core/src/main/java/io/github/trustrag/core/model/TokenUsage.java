package io.github.trustrag.core.model;

public record TokenUsage(int promptTokens, int completionTokens) {

    public static TokenUsage unknown() {
        return new TokenUsage(0, 0);
    }
}
