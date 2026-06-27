package io.github.trustrag.document;

/**
 * ParsedDocumentSection 是不可变领域数据对象，用于在服务之间传递结构化信息。
 */
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
