package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ScopeContext;

/**
 * ScopeResolver 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
@FunctionalInterface
public interface ScopeResolver {

    ScopeContext resolve(RagRequest request);
}
