package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.spi.ScopeResolver;

/**
 * DefaultScopeResolver 承载 trust-rag-core 模块中的领域逻辑或基础设施适配职责。
 */
public final class DefaultScopeResolver implements ScopeResolver {

    @Override
    public ScopeContext resolve(RagRequest request) {
        return ScopeContext.from(request);
    }
}
