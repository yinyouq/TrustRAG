package io.github.trustrag.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ParserSupport {

    private ParserSupport() {
    }

    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    static String fallbackTitle(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Untitled document";
        }
        String normalized = filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = normalized.substring(separator + 1);
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    static final class Sections {

        private final String sourceTitle;
        private final String sourceUrl;
        private final List<ParsedDocumentSection> sections = new ArrayList<>();
        private final StringBuilder content = new StringBuilder();
        private String title;
        private String path;
        private Integer pageNumber;

        Sections(String sourceTitle, String sourceUrl) {
            this.sourceTitle = sourceTitle;
            this.sourceUrl = sourceUrl;
            this.title = sourceTitle;
        }

        void begin(String newTitle, String newPath, Integer newPageNumber) {
            flush();
            title = newTitle == null || newTitle.isBlank() ? sourceTitle : newTitle.trim();
            path = newPath;
            pageNumber = newPageNumber;
        }

        void append(String value) {
            String normalized = normalizeText(value);
            if (normalized.isBlank()) {
                return;
            }
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(normalized);
        }

        List<ParsedDocumentSection> finish() {
            flush();
            return List.copyOf(sections);
        }

        private void flush() {
            String normalized = normalizeText(content.toString());
            if (!normalized.isBlank()) {
                sections.add(new ParsedDocumentSection(
                        sourceTitle, title, normalized, sourceUrl, pageNumber, path));
            }
            content.setLength(0);
        }
    }
}
