package io.github.trustrag.document;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HtmlDocumentParser implements DocumentParser {

    private static final Set<String> IGNORED_TAGS =
            Set.of("script", "style", "noscript", "nav", "svg", "canvas");

    @Override
    public boolean supports(String filename, String contentType) {
        String extension = ParserSupport.extension(filename);
        return extension.equals("html") || extension.equals("htm")
                || contentType != null && contentType.toLowerCase(Locale.ROOT).contains("html");
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        Document document = Jsoup.parse(path.toFile(), StandardCharsets.UTF_8.name(), sourceUrl);
        String sourceTitle = document.title();
        if (sourceTitle == null || sourceTitle.isBlank()) {
            sourceTitle = ParserSupport.fallbackTitle(filename);
        }
        ParserSupport.Sections result = new ParserSupport.Sections(sourceTitle, sourceUrl);
        List<String> headingPath = new ArrayList<>();
        result.begin(sourceTitle, null, null);
        Element body = document.body();
        if (body != null) {
            walk(body, headingPath, result);
        }
        return result.finish();
    }

    private void walk(
            Element element,
            List<String> headingPath,
            ParserSupport.Sections result) {
        String tag = element.normalName();
        if (IGNORED_TAGS.contains(tag)) {
            return;
        }
        if (tag.matches("h[1-6]")) {
            String heading = ParserSupport.normalizeText(element.text());
            if (!heading.isBlank()) {
                int level = Character.digit(tag.charAt(1), 10);
                while (headingPath.size() >= level) {
                    headingPath.remove(headingPath.size() - 1);
                }
                headingPath.add(heading);
                result.begin(heading, String.join(" / ", headingPath), null);
            }
            return;
        }
        String ownText = ParserSupport.normalizeText(element.ownText());
        if (!ownText.isBlank()) {
            result.append(ownText);
        }
        for (Element child : element.children()) {
            walk(child, headingPath, result);
        }
    }
}
