package io.github.trustrag.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class UploadDocumentSourceLoader implements DocumentSourceLoader {

    private final LocalDocumentStorage storage;

    public UploadDocumentSourceLoader(LocalDocumentStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean supports(DocumentSourceKind sourceKind) {
        return sourceKind == DocumentSourceKind.UPLOAD;
    }

    @Override
    public LoadedDocumentSource load(DocumentImportTask task) throws IOException {
        Path path = storage.requireStoredFile(task.storagePath());
        String sourceUrl = task.sourceUri() == null || task.sourceUri().isBlank()
                ? "upload://" + task.taskId() + "/" + task.originalFilename()
                : task.sourceUri();
        DocumentResource resource = new DocumentResource(
                path, task.originalFilename(), task.contentType(), sourceUrl);
        return new LoadedDocumentSource(List.of(resource), null);
    }
}
