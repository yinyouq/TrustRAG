package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagFeedbackRequest;

/**
 * FeedbackRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface FeedbackRepository {

    long save(RagFeedbackRequest request);
}
