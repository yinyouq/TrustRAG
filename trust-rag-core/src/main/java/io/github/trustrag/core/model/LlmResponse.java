package io.github.trustrag.core.model;

/**
 * LlmResponse 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record LlmResponse(String text, Double confidence, TokenUsage tokenUsage) {

    public LlmResponse {
        tokenUsage = tokenUsage == null ? TokenUsage.unknown() : tokenUsage;
    }
}
