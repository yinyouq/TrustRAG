package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictCheckResult;
import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * ConflictDetector 负责检测特定风险或信号，并把判断结果交给治理流程使用。
 */
public interface ConflictDetector {

    ConflictCheckResult check(KnowledgeItem candidate, List<Float> candidateVector);
}
