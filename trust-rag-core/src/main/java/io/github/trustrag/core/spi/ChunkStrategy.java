package io.github.trustrag.core.spi;

import java.util.List;

/**
 * ChunkStrategy 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
@FunctionalInterface
public interface ChunkStrategy {

    List<String> split(String content);
}
