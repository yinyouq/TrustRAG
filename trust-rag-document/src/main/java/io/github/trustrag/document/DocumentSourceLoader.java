package io.github.trustrag.document;

import java.io.IOException;

/**
 * DocumentSourceLoader 负责加载外部文档来源，并转换为统一的文档资源列表。
 */
public interface DocumentSourceLoader {

    boolean supports(DocumentSourceKind sourceKind);

    LoadedDocumentSource load(DocumentImportTask task) throws IOException;
}
