package io.github.trustrag.core.config;

public record EngineOptions(
        boolean queryRewriteEnabled,
        boolean rerankEnabled,
        boolean gapDetectionEnabled,
        boolean candidateExtractionEnabled,
        int defaultTopK,
        int promptMaxChunks,
        boolean savePrompt) {

    public EngineOptions {
        if (defaultTopK < 1 || promptMaxChunks < 1) {
            throw new IllegalArgumentException("TopK and promptMaxChunks must be positive");
        }
    }
}
