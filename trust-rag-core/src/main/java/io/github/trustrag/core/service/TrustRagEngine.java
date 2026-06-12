package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;

public interface TrustRagEngine {

    RagAnswer ask(RagRequest request);
}
