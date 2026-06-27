package io.github.trustrag.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * DocumentParser 负责把特定格式文档解析为可导入的标准章节。
 */
public interface DocumentParser {

    boolean supports(String filename, String contentType);

    List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException;
}
