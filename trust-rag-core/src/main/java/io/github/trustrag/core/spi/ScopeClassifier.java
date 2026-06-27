package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;

/**
 * ScopeClassifier 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
@FunctionalInterface
public interface ScopeClassifier {

    ScopeType classify(String content, ScopeContext context);
}
