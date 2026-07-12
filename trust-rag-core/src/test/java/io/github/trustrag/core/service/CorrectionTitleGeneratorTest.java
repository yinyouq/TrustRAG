package io.github.trustrag.core.service;

import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.spi.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionTitleGeneratorTest {

    @Test
    void generatesQuestionTitleFromOriginalQuestionAndCorrection() {
        CapturingLlmClient llmClient = new CapturingLlmClient(
                "1. \"What weapons, armor, and accessories are included?\"");
        CorrectionTitleGenerator generator = new CorrectionTitleGenerator(llmClient);

        String title = generator.generate(
                "What does the equipment list include?",
                "Weapons include fourteen melee/ranged weapons. Armor includes head, chest, hand, waist, and leg parts.");

        assertThat(title).isEqualTo("What weapons, armor, and accessories are included?");
        assertThat(llmClient.prompt)
                .contains("What does the equipment list include?")
                .contains("Weapons include fourteen melee/ranged weapons.");
    }

    @Test
    void fallsBackToOriginalQuestionWhenModelFails() {
        CorrectionTitleGenerator generator = new CorrectionTitleGenerator(prompt -> {
            throw new IllegalStateException("model unavailable");
        });

        String title = generator.generate(
                "How can Spring AI register tools dynamically?",
                "Spring AI can register tools dynamically through ToolCallback.");

        assertThat(title).isEqualTo("How can Spring AI register tools dynamically?");
    }

    private static final class CapturingLlmClient implements LlmClient {
        private final String response;
        private String prompt;

        private CapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public LlmResponse generate(String prompt) {
            this.prompt = prompt;
            return new LlmResponse(response, null, TokenUsage.unknown());
        }
    }
}
