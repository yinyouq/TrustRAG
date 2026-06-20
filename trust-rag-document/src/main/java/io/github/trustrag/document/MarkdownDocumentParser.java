package io.github.trustrag.document;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarkdownDocumentParser implements DocumentParser {

    private final Parser parser = Parser.builder().build();
    private final TextCollectingVisitor textVisitor = new TextCollectingVisitor();

    @Override
    public boolean supports(String filename, String contentType) {
        String extension = ParserSupport.extension(filename);
        return extension.equals("md") || extension.equals("markdown")
                || contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains("markdown");
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        String markdown = Files.readString(path, StandardCharsets.UTF_8);
        Node document = parser.parse(markdown);
        String sourceTitle = ParserSupport.fallbackTitle(filename);
        ParserSupport.Sections result = new ParserSupport.Sections(sourceTitle, sourceUrl);
        List<String> headingPath = new ArrayList<>();
        result.begin(sourceTitle, null, null);
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                String text = ParserSupport.normalizeText(heading.getText().toString());
                int level = heading.getLevel();
                while (headingPath.size() >= level) {
                    headingPath.remove(headingPath.size() - 1);
                }
                headingPath.add(text);
                result.begin(text, String.join(" / ", headingPath), null);
            } else {
                result.append(textVisitor.collectAndGetText(node));
            }
        }
        return result.finish();
    }
}
