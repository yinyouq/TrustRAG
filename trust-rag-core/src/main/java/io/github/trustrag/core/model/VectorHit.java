package io.github.trustrag.core.model;

/**
 * VectorHit 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record VectorHit(long knowledgeId, double vectorScore) {
}
