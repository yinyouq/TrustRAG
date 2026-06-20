package io.github.trustrag.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiDocumentParser implements DocumentParser {

    private static final Pattern HEADING = Pattern.compile("^(={1,6})\\s*(.*?)\\s*\\1$");
    private static final Pattern LINK = Pattern.compile("\\[\\[(?:[^]|]+\\|)?([^]]+)]]");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("\\[(?:https?://\\S+)\\s+([^]]+)]");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^{}]*}}");

    @Override
    public boolean supports(String filename, String contentType) {
        String extension = ParserSupport.extension(filename);
        return extension.equals("wiki") || extension.equals("mediawiki");
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        String sourceTitle = ParserSupport.fallbackTitle(filename);
        ParserSupport.Sections result = new ParserSupport.Sections(sourceTitle, sourceUrl);
        List<String> headingPath = new ArrayList<>();
        result.begin(sourceTitle, null, null);
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher heading = HEADING.matcher(line.trim());
            if (heading.matches()) {
                int level = heading.group(1).length();
                String title = clean(heading.group(2));
                while (headingPath.size() >= level) {
                    headingPath.remove(headingPath.size() - 1);
                }
                headingPath.add(title);
                result.begin(title, String.join(" / ", headingPath), null);
            } else {
                result.append(clean(line));
            }
        }
        return result.finish();
    }

    private String clean(String value) {
        String result = TEMPLATE.matcher(value).replaceAll(" ");
        result = LINK.matcher(result).replaceAll("$1");
        result = EXTERNAL_LINK.matcher(result).replaceAll("$1");
        return result
                .replaceAll("'{2,5}", "")
                .replaceAll("<[^>]+>", " ")
                .trim();
    }
}
