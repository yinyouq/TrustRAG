package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.PrivacyResult;

/**
 * PrivacyFilter 负责过滤或约束输入内容，降低不合规数据进入知识库的风险。
 */
@FunctionalInterface
public interface PrivacyFilter {

    PrivacyResult filter(String content);
}
