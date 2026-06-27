package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.EvidenceVerificationResult;
import io.github.trustrag.core.model.KnowledgeItem;

/**
 * EvidenceVerifier 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
public interface EvidenceVerifier {

    EvidenceVerificationResult verify(KnowledgeItem candidate);
}
