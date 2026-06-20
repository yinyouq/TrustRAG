package io.github.trustrag.document;

import java.util.List;

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
