package io.github.trustrag.document;

import java.util.List;

/**
 * LoadedDocumentSource 承载 trust-rag-document 模块中的领域逻辑或基础设施适配职责。
 */
public final class LoadedDocumentSource implements AutoCloseable {

    private final List<DocumentResource> resources;
    private final Runnable cleanup;

    public LoadedDocumentSource(List<DocumentResource> resources, Runnable cleanup) {
        this.resources = List.copyOf(resources);
        this.cleanup = cleanup == null ? () -> { } : cleanup;
    }

    public List<DocumentResource> resources() {
        return resources;
    }

    @Override
    public void close() {
        cleanup.run();
    }
}
