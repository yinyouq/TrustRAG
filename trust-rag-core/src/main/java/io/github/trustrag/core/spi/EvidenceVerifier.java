package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.EvidenceVerificationResult;
import io.github.trustrag.core.model.KnowledgeItem;

public interface EvidenceVerifier {

    EvidenceVerificationResult verify(KnowledgeItem candidate);
}
