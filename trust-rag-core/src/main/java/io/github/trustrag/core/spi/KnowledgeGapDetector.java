package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.RagTrace;

@FunctionalInterface
public interface KnowledgeGapDetector {

    GapDetectionResult detect(RagTrace trace);
}
