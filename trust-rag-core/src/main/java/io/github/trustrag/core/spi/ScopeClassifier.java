package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;

@FunctionalInterface
public interface ScopeClassifier {

    ScopeType classify(String content, ScopeContext context);
}
