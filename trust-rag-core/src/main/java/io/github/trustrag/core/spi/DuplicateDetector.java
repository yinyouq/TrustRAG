package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.DuplicateCheckResult;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * DuplicateDetector 负责检测特定风险或信号，并把判断结果交给治理流程使用。
 */
public interface DuplicateDetector {

    DuplicateCheckResult check(KnowledgeItem candidate, List<Float> candidateVector);
}
