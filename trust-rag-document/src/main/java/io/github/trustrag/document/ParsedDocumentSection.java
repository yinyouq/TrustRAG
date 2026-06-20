package io.github.trustrag.document;

public record ParsedDocumentSection(
        String sourceTitle,
        String title,
        String content,
        String sourceUrl,
        Integer pageNumber,
        String sectionPath) {

    public ParsedDocumentSection {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Parsed document section content must not be blank");
        }
    }
}
