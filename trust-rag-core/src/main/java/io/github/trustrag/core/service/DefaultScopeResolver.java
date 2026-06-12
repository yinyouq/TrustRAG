package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.spi.ScopeResolver;

public final class DefaultScopeResolver implements ScopeResolver {

    @Override
    public ScopeContext resolve(RagRequest request) {
        return ScopeContext.from(request);
    }
}
