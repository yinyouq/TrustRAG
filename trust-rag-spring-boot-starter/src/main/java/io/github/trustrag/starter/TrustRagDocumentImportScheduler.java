package io.github.trustrag.starter;

import io.github.trustrag.document.DocumentImportWorker;
import org.springframework.scheduling.annotation.Scheduled;

public final class TrustRagDocumentImportScheduler {

    private final DocumentImportWorker worker;

    public TrustRagDocumentImportScheduler(DocumentImportWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString = "${trust-rag.document.fixed-delay-ms:5000}",
            initialDelayString = "${trust-rag.document.initial-delay-ms:3000}")
    public void run() {
        worker.runBatch();
    }
}
