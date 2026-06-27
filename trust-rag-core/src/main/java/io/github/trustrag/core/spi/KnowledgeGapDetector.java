package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.RagTrace;

/**
 * KnowledgeGapDetector 负责检测特定风险或信号，并把判断结果交给治理流程使用。
 */
@FunctionalInterface
public interface KnowledgeGapDetector {

    GapDetectionResult detect(RagTrace trace);
}
