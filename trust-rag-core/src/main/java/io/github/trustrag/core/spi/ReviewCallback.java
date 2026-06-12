package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;

@FunctionalInterface
public interface ReviewCallback {

    void onApproved(KnowledgeItem knowledge);
}
