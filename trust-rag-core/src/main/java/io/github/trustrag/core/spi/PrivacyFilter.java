package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.PrivacyResult;

@FunctionalInterface
public interface PrivacyFilter {

    PrivacyResult filter(String content);
}
