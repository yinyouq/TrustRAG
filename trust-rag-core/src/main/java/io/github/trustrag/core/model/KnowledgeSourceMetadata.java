package io.github.trustrag.core.model;

public record KnowledgeSourceMetadata(
        String sourceTitle,
        String sourceUrl,
        Integer pageNumber,
        String sectionPath,
        String documentId,
        Integer chunkIndex) {

    public static KnowledgeSourceMetadata empty() {
        return new KnowledgeSourceMetadata(null, null, null, null, null, null);
    }

    public KnowledgeSourceMetadata withChunkIndex(int value) {
        return new KnowledgeSourceMetadata(
                sourceTitle, sourceUrl, pageNumber, sectionPath, documentId, value);
    }
}
