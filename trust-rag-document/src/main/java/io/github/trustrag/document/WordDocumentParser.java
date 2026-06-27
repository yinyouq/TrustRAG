package io.github.trustrag.document;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WordDocumentParser 负责把特定格式文档解析为可导入的标准章节。
 */
public final class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename, String contentType) {
        String extension = ParserSupport.extension(filename);
        return extension.equals("doc") || extension.equals("docx")
                || contentType != null
                && (contentType.contains("word") || contentType.contains("officedocument"));
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        return "doc".equals(ParserSupport.extension(filename))
                ? parseLegacy(path, filename, sourceUrl)
                : parseOpenXml(path, filename, sourceUrl);
    }

    private List<ParsedDocumentSection> parseOpenXml(
            Path path,
            String filename,
            String sourceUrl) throws IOException {
        String sourceTitle = ParserSupport.fallbackTitle(filename);
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input)) {
            ParserSupport.Sections result = new ParserSupport.Sections(sourceTitle, sourceUrl);
            List<String> headingPath = new ArrayList<>();
            result.begin(sourceTitle, null, null);
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = ParserSupport.normalizeText(paragraph.getText());
                    if (text.isBlank()) {
                        continue;
                    }
                    int headingLevel = headingLevel(paragraph);
                    if (headingLevel > 0) {
                        while (headingPath.size() >= headingLevel) {
                            headingPath.remove(headingPath.size() - 1);
                        }
                        headingPath.add(text);
                        String sectionPath = String.join(" / ", headingPath);
                        result.begin(text, sectionPath, null);
                    } else {
                        result.append(text);
                    }
                } else if (element instanceof XWPFTable table) {
                    result.append(tableText(table));
                }
            }
            return result.finish();
        }
    }

    private List<ParsedDocumentSection> parseLegacy(
            Path path,
            String filename,
            String sourceUrl) throws IOException {
        String sourceTitle = ParserSupport.fallbackTitle(filename);
        try (InputStream input = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            String text = ParserSupport.normalizeText(extractor.getText());
            return text.isBlank()
                    ? List.of()
                    : List.of(new ParsedDocumentSection(
                            sourceTitle, sourceTitle, text, sourceUrl, null, null));
        }
    }

    private int headingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return 0;
        }
        String normalized = style.toLowerCase(Locale.ROOT);
        if (!normalized.contains("heading") && !normalized.contains("title")) {
            return 0;
        }
        for (int index = normalized.length() - 1; index >= 0; index--) {
            if (Character.isDigit(normalized.charAt(index))) {
                return Math.max(1, Character.digit(normalized.charAt(index), 10));
            }
        }
        return 1;
    }

    private String tableText(XWPFTable table) {
        StringBuilder result = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            boolean first = true;
            for (XWPFTableCell cell : row.getTableCells()) {
                if (!first) {
                    result.append(" | ");
                }
                result.append(ParserSupport.normalizeText(cell.getText()));
                first = false;
            }
        }
        return result.toString();
    }
}
