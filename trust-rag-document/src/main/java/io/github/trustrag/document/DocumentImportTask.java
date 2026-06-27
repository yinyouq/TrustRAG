package io.github.trustrag.document;

import java.time.Instant;
import java.util.UUID;

/**
 * DocumentImportTask 表示后台任务记录，跟踪任务状态、重试和错误信息。
 */
public record DocumentImportTask(
        Long id,
        String taskId,
        DocumentSourceKind sourceKind,
        DocumentImportStatus status,
        String originalFilename,
        String contentType,
        String sourceUri,
        String storagePath,
        String gitRef,
        DocumentImportOptions options,
        int totalDocuments,
        int totalSections,
        int importedCount,
        int duplicateCount,
        int failedCount,
        int retryCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt) {

    public static DocumentImportTask pendingUpload(
            String filename,
            String contentType,
            String storagePath,
            DocumentImportOptions options,
            Instant now) {
        return pendingUpload(
                filename, contentType, null, storagePath, options, now);
    }

    public static DocumentImportTask pendingUpload(
            String filename,
            String contentType,
            String sourceUrl,
            String storagePath,
            DocumentImportOptions options,
            Instant now) {
        return pending(
                DocumentSourceKind.UPLOAD, filename, contentType, sourceUrl,
                storagePath, null, options, now);
    }

    public static DocumentImportTask pendingGit(
            String repositoryUri,
            String gitRef,
            DocumentImportOptions options,
            Instant now) {
        return pending(
                DocumentSourceKind.GIT, null, null, repositoryUri,
                null, gitRef, options, now);
    }

    private static DocumentImportTask pending(
            DocumentSourceKind sourceKind,
            String filename,
            String contentType,
            String sourceUri,
            String storagePath,
            String gitRef,
            DocumentImportOptions options,
            Instant now) {
        return new DocumentImportTask(
                null, UUID.randomUUID().toString(), sourceKind, DocumentImportStatus.PENDING,
                filename, contentType, sourceUri, storagePath, gitRef, options,
                0, 0, 0, 0, 0, 0, null, now, null, null, now);
    }

    public DocumentImportTask withId(long value) {
        return copy(
                value, status, totalDocuments, totalSections, importedCount,
                duplicateCount, failedCount, retryCount, errorMessage,
                startedAt, finishedAt, updatedAt);
    }

    public DocumentImportTask start(Instant now) {
        return copy(
                id, DocumentImportStatus.PROCESSING, 0, 0, 0, 0, 0,
                retryCount, null, now, null, now);
    }

    public DocumentImportTask complete(
            int documents,
            int sections,
            int imported,
            int duplicates,
            int failures,
            Instant now) {
        DocumentImportStatus finalStatus;
        int finalRetryCount = retryCount;
        if (failures == 0) {
            finalStatus = DocumentImportStatus.COMPLETED;
        } else if (imported > 0 || duplicates > 0) {
            finalStatus = DocumentImportStatus.PARTIAL;
        } else {
            finalStatus = DocumentImportStatus.FAILED;
            finalRetryCount++;
        }
        return copy(
                id, finalStatus, documents, sections, imported, duplicates, failures,
                finalRetryCount,
                failures == 0 ? null : "One or more document sections failed",
                startedAt, now, now);
    }

    public DocumentImportTask fail(String reason, Instant now) {
        return copy(
                id, DocumentImportStatus.FAILED, totalDocuments, totalSections,
                importedCount, duplicateCount, Math.max(1, failedCount),
                retryCount + 1, abbreviate(reason, 4000), startedAt, now, now);
    }

    public DocumentImportTask retry(Instant now) {
        return copy(
                id, DocumentImportStatus.PENDING, 0, 0, 0, 0, 0,
                retryCount, null, null, null, now);
    }

    private DocumentImportTask copy(
            Long newId,
            DocumentImportStatus newStatus,
            int newTotalDocuments,
            int newTotalSections,
            int newImportedCount,
            int newDuplicateCount,
            int newFailedCount,
            int newRetryCount,
            String newErrorMessage,
            Instant newStartedAt,
            Instant newFinishedAt,
            Instant newUpdatedAt) {
        return new DocumentImportTask(
                newId, taskId, sourceKind, newStatus, originalFilename, contentType,
                sourceUri, storagePath, gitRef, options, newTotalDocuments,
                newTotalSections, newImportedCount, newDuplicateCount, newFailedCount,
                newRetryCount, newErrorMessage, createdAt, newStartedAt,
                newFinishedAt, newUpdatedAt);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
