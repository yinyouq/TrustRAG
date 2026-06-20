package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptBuilderTest {

    @Test
    void labelsAllTrustLevelsAndKeepsHighBeforeMediumBeforeLow() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder(List.of());

        String prompt = builder.build(
                RagRequest.builder().question("question").build(),
                List.of(
                        chunk(3L, TrustLevel.LOW),
                        chunk(2L, TrustLevel.MEDIUM),
                        chunk(1L, TrustLevel.HIGH)));

        assertThat(prompt).contains("[high][knowledge_id=1]");
        assertThat(prompt).contains("[medium][knowledge_id=2]");
        assertThat(prompt).contains("[low][knowledge_id=3]");
        assertThat(prompt.indexOf("[high]")).isLessThan(prompt.indexOf("[medium]"));
        assertThat(prompt.indexOf("[medium]")).isLessThan(prompt.indexOf("[low]"));
    }

    private RetrievedChunk chunk(long id, TrustLevel trustLevel) {
        return new RetrievedChunk(
                id, "title-" + id, "content-" + id, "source-" + id,
                trustLevel, ScopeType.GLOBAL, 0.9, null, 1.0, 0.9, true);
    }
}
