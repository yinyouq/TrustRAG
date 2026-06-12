package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.ScopeContext;

@FunctionalInterface
public interface ScopeResolver {

    ScopeContext resolve(RagRequest request);
}
