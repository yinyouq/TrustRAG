package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.DuplicateCheckResult;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

public interface DuplicateDetector {

    DuplicateCheckResult check(KnowledgeItem candidate, List<Float> candidateVector);
}
