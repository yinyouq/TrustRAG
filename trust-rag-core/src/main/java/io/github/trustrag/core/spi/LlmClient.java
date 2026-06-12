package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.LlmResponse;

public interface LlmClient {

    LlmResponse generate(String prompt);
}
