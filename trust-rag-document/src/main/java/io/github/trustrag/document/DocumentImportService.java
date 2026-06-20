package io.github.trustrag.document;

import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.exception.TrustRagException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

public final class DocumentImportService {

    private final DocumentImportTaskRepository taskRepository;
    private final LocalDocumentStorage storage;
    private final DocumentImportSettings settings;
    private final GitSourcePolicy gitSourcePolicy;
    private final Clock clock;

    public DocumentImportService(
            DocumentImportTaskRepository taskRepository,
            LocalDocumentStorage storage,
            DocumentImportSettings settings,
            GitSourcePolicy gitSourcePolicy,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.storage = storage;
        this.settings = settings;
        this.gitSourcePolicy = gitSourcePolicy;
        this.clock = clock;
    }

    public DocumentImportTask submitUpload(
            String filename,
            String contentType,
            String sourceUrl,
            InputStream input,
            long size,
            DocumentImportOptions options) {
        validateOptions(options);
        if (filename == null || filename.isBlank()) {
            throw new InvalidRagRequestException("filename must not be blank");
        }
        String extension = ParserSupport.extension(filename);
        if (!settings.allowedExtensions().contains(extension)) {
            throw new InvalidRagRequestException("Unsupported document extension: " + extension);
        }
        try {
            var path = storage.store(filename, input, size);
            try {
                return taskRepository.save(DocumentImportTask.pendingUpload(
                        path.getFileName().toString(),
                        contentType,
                        normalizeOptionalUrl(sourceUrl),
                        path.toString(),
                        options,
                        clock.instant()));
            } catch (RuntimeException exception) {
                storage.delete(path);
                throw exception;
            }
        } catch (IOException exception) {
            throw new TrustRagException("Document upload failed", exception);
        }
    }

    public DocumentImportTask submitGit(
            String repositoryUri,
            String gitRef,
            DocumentImportOptions options) {
        validateOptions(options);
        String validatedUri = gitSourcePolicy.validate(repositoryUri);
        return taskRepository.save(DocumentImportTask.pendingGit(
                validatedUri, gitRef, options, clock.instant()));
    }

    public DocumentImportTask find(String taskId) {
        return taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new DocumentImportTaskNotFoundException(taskId));
    }

    public DocumentImportTask retry(String taskId) {
        DocumentImportTask task = find(taskId);
        if (task.status() != DocumentImportStatus.FAILED
                && task.status() != DocumentImportStatus.PARTIAL) {
            throw new InvalidRagRequestException(
                    "Only FAILED or PARTIAL document tasks can be retried");
        }
        if (task.retryCount() >= settings.retryLimit()) {
            throw new InvalidRagRequestException("Document task retry limit reached");
        }
        DocumentImportTask retry = task.retry(clock.instant());
        taskRepository.update(retry);
        return retry;
    }

    private void validateOptions(DocumentImportOptions options) {
        if (options == null) {
            throw new InvalidRagRequestException("Document import options must not be null");
        }
        if (options.trustLevel() == io.github.trustrag.core.model.TrustLevel.LOW
                && options.scopeType() == io.github.trustrag.core.model.ScopeType.GLOBAL) {
            throw new InvalidRagRequestException("Low-trust documents cannot use GLOBAL scope");
        }
    }

    private String normalizeOptionalUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(sourceUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https")
                    && !scheme.equalsIgnoreCase("http"))) {
                throw new InvalidRagRequestException(
                        "sourceUrl must use http or https");
            }
            if (uri.getUserInfo() != null) {
                throw new InvalidRagRequestException(
                        "sourceUrl must not contain credentials");
            }
            return uri.toString();
        } catch (java.net.URISyntaxException exception) {
            throw new InvalidRagRequestException("sourceUrl is invalid");
        }
    }
}
