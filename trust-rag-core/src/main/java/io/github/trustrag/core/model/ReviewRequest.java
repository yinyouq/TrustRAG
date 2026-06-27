package io.github.trustrag.core.model;

/**
 * ReviewRequest 表示一次领域请求，承载调用方传入的业务参数。
 */
public record ReviewRequest(
        String reviewerId,
        String comment,
        String modifiedTitle,
        String modifiedContent) {
}
