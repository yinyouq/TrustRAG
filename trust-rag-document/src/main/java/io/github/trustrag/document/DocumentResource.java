package io.github.trustrag.document;

import java.nio.file.Path;

public record DocumentResource(
        Path path,
        String filename,
        String contentType,
        String sourceUrl) {
}
