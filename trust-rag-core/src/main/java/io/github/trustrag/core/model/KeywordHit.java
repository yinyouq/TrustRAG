package io.github.trustrag.core.model;

/**
 * KeywordHit 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record KeywordHit(long knowledgeId, double keywordScore) {
}
