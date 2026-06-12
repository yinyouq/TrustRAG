package io.github.trustrag.core.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveTextChunkStrategyTest {

    @Test
    void splitsLongTextWithoutLooping() {
        RecursiveTextChunkStrategy strategy = new RecursiveTextChunkStrategy(100, 20);
        String content = "A paragraph used to test recursive chunking. ".repeat(40);

        var chunks = strategy.split(content);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> !chunk.isBlank() && chunk.length() <= 100);
    }
}
