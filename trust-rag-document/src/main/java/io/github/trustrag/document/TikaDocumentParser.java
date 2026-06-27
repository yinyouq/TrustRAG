package io.github.trustrag.document;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * TikaDocumentParser 负责把特定格式文档解析为可导入的标准章节。
 */
public final class TikaDocumentParser implements DocumentParser {

    private static final int MAX_EXTRACTED_CHARACTERS = 2_000_000;
    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public boolean supports(String filename, String contentType) {
        return true;
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        Metadata metadata = new Metadata();
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, new ParseContext());
        } catch (SAXException | org.apache.tika.exception.TikaException exception) {
            throw new IOException("Apache Tika could not parse " + filename, exception);
        }
        String content = ParserSupport.normalizeText(handler.toString());
        if (content.isBlank()) {
            return List.of();
        }
        String sourceTitle = metadata.get("dc:title");
        if (sourceTitle == null || sourceTitle.isBlank()) {
            sourceTitle = ParserSupport.fallbackTitle(filename);
        }
        return List.of(new ParsedDocumentSection(
                sourceTitle, sourceTitle, content, sourceUrl, null, null));
    }
}
