package io.github.trustrag.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 组合文档解析器。
 *
 * <p>按注册顺序选择第一个支持当前文件的解析器，方便后续插入更专业的解析实现。</p>
 */
public final class CompositeDocumentParser implements DocumentParser {

    private final List<DocumentParser> parsers;

    public CompositeDocumentParser(List<DocumentParser> parsers) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("At least one document parser is required");
        }
        this.parsers = List.copyOf(parsers);
    }

    @Override
    public boolean supports(String filename, String contentType) {
        return parsers.stream().anyMatch(parser -> parser.supports(filename, contentType));
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        return parsers.stream()
                .filter(parser -> parser.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> new IOException("Unsupported document type: " + filename))
                .parse(path, filename, contentType, sourceUrl);
    }
}
