package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.LlmResponse;

/**
 * LlmClient 适配外部客户端能力，向核心模块暴露统一 SPI。
 */
public interface LlmClient {

    LlmResponse generate(String prompt);
}
