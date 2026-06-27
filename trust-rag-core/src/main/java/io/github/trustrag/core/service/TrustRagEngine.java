package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;

/**
 * TrustRagEngine 是领域引擎入口，编排多个服务完成一次完整业务流程。
 */
public interface TrustRagEngine {

    RagAnswer ask(RagRequest request);
}
