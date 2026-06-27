package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

/**
 * PromptCustomizer 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
@FunctionalInterface
public interface PromptCustomizer {

    String customize(String prompt, RagRequest request, List<RetrievedChunk> chunks);
}
