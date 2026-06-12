package io.github.trustrag.core.spi;

import java.util.List;

@FunctionalInterface
public interface ChunkStrategy {

    List<String> split(String content);
}
