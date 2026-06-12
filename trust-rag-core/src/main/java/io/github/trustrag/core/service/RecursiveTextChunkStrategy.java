package io.github.trustrag.core.service;

import io.github.trustrag.core.spi.ChunkStrategy;

import java.util.ArrayList;
import java.util.List;

public final class RecursiveTextChunkStrategy implements ChunkStrategy {

    private final int chunkSize;
    private final int overlap;

    public RecursiveTextChunkStrategy(int chunkSize, int overlap) {
        if (chunkSize < 100) {
            throw new IllegalArgumentException("chunkSize must be at least 100");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be between 0 and chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n").trim();
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + chunkSize, normalized.length());
            int end = findBoundary(normalized, start, hardEnd);
            chunks.add(normalized.substring(start, end).trim());
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks.stream().filter(value -> !value.isBlank()).toList();
    }

    private int findBoundary(String content, int start, int hardEnd) {
        if (hardEnd == content.length()) {
            return hardEnd;
        }
        int minimum = start + chunkSize / 2;
        for (int index = hardEnd; index >= minimum; index--) {
            char current = content.charAt(index - 1);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index;
            }
        }
        return hardEnd;
    }
}
