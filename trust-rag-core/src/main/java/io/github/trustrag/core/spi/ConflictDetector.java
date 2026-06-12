package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictCheckResult;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

public interface ConflictDetector {

    ConflictCheckResult check(KnowledgeItem candidate, List<Float> candidateVector);
}
