package io.github.trustrag.core.model;

/**
 * TokenUsage 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record TokenUsage(int promptTokens, int completionTokens) {

    public static TokenUsage unknown() {
        return new TokenUsage(0, 0);
    }
}
