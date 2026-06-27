package io.github.trustrag.document;

import java.nio.file.Path;

/**
 * DocumentResource 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
public record DocumentResource(
        Path path,
        String filename,
        String contentType,
        String sourceUrl) {
}
