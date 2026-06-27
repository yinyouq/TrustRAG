package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievedChunk;

import java.util.List;

/**
 * PromptBuilder 负责按领域规则构建请求、查询或输出内容。
 */
@FunctionalInterface
public interface PromptBuilder {

    String build(RagRequest request, List<RetrievedChunk> chunks);
}
