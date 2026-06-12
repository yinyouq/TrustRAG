package io.github.trustrag.starter;

import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.spi.LlmClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

public final class SpringAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmResponse generate(String prompt) {
        ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(prompt));
        String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return new LlmResponse(text, null, new TokenUsage(promptTokens, completionTokens));
    }
}
