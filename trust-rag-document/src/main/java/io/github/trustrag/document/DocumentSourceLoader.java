package io.github.trustrag.document;

import java.io.IOException;

public interface DocumentSourceLoader {

    boolean supports(DocumentSourceKind sourceKind);

    LoadedDocumentSource load(DocumentImportTask task) throws IOException;
}
