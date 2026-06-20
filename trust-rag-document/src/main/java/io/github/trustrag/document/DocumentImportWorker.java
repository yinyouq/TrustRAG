package io.github.trustrag.document;

import io.github.trustrag.core.model.KnowledgeImportRequest;
import io.github.trustrag.core.model.KnowledgeImportResult;
import io.github.trustrag.core.model.KnowledgeSourceMetadata;
import io.github.trustrag.core.service.KnowledgeIngestionService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public final class DocumentImportWorker {

    private static final System.Logger LOGGER =
            System.getLogger(DocumentImportWorker.class.getName());

    private final DocumentImportTaskRepository taskRepository;
    private final KnowledgeIngestionService ingestionService;
    private final DocumentParser parser;
    private final List<DocumentSourceLoader> sourceLoaders;
    private final DocumentImportSettings settings;
    private final LocalDocumentStorage storage;
    private final Clock clock;

    public DocumentImportWorker(
            DocumentImportTaskRepository taskRepository,
            KnowledgeIngestionService ingestionService,
            DocumentParser parser,
            List<DocumentSourceLoader> sourceLoaders,
            DocumentImportSettings settings,
            LocalDocumentStorage storage,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.ingestionService = ingestionService;
        this.parser = parser;
        this.sourceLoaders = List.copyOf(sourceLoaders);
        this.settings = settings;
        this.storage = storage;
        this.clock = clock;
    }

    public int runBatch() {
        int processed = 0;
        for (DocumentImportTask task : taskRepository.findRunnable(
                settings.retryLimit(), settings.batchSize())) {
            Instant now = clock.instant();
            if (!taskRepository.claim(task, now)) {
                continue;
            }
            process(task.start(now));
            processed++;
        }
        return processed;
    }

    private void process(DocumentImportTask task) {
        try {
            taskRepository.update(task);
            DocumentSourceLoader loader = sourceLoaders.stream()
                    .filter(value -> value.supports(task.sourceKind()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No document source loader for " + task.sourceKind()));
            int documents = 0;
            int sections = 0;
            int imported = 0;
            int duplicates = 0;
            int failures = 0;
            try (LoadedDocumentSource source = loader.load(task)) {
                for (DocumentResource resource : source.resources()) {
                    documents++;
                    try {
                        List<ParsedDocumentSection> parsed = parser.parse(
                                resource.path(), resource.filename(),
                                resource.contentType(), resource.sourceUrl());
                        if (parsed.isEmpty()) {
                            failures++;
                            continue;
                        }
                        for (ParsedDocumentSection section : parsed) {
                            sections++;
                            KnowledgeImportResult result = importSection(task, section);
                            imported += result.importedCount();
                            duplicates += result.duplicateCount();
                            failures += result.failedCount();
                        }
                    } catch (Exception exception) {
                        failures++;
                        LOGGER.log(
                                System.Logger.Level.WARNING,
                                "Document resource import failed: " + resource.filename(),
                                exception);
                    }
                }
            }
            DocumentImportTask completed = task.complete(
                    documents, sections, imported, duplicates, failures, clock.instant());
            taskRepository.update(completed);
            if (completed.status() == DocumentImportStatus.COMPLETED
                    && completed.sourceKind() == DocumentSourceKind.UPLOAD
                    && !settings.keepSourceFiles()) {
                storage.delete(java.nio.file.Path.of(completed.storagePath()));
            }
        } catch (Exception exception) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Document import task failed: " + task.taskId(),
                    exception);
            taskRepository.update(task.fail(rootMessage(exception), clock.instant()));
        }
    }

    private KnowledgeImportResult importSection(
            DocumentImportTask task,
            ParsedDocumentSection section) {
        DocumentImportOptions options = task.options();
        String title = options.title() == null || options.title().isBlank()
                ? section.title()
                : options.title().trim() + " / " + section.title();
        String sourceRef = sourceRef(task, section);
        return ingestionService.importKnowledge(new KnowledgeImportRequest(
                title,
                section.content(),
                options.sourceType(),
                sourceRef,
                options.trustLevel(),
                options.scopeType(),
                options.userId(),
                options.conversationId(),
                options.projectId(),
                options.tenantId(),
                new KnowledgeSourceMetadata(
                        options.title() == null || options.title().isBlank()
                                ? section.sourceTitle()
                                : options.title().trim(),
                        section.sourceUrl(),
                        section.pageNumber(),
                        section.sectionPath(),
                        task.taskId(),
                        null)));
    }

    private String sourceRef(DocumentImportTask task, ParsedDocumentSection section) {
        String base = section.sourceUrl() == null || section.sourceUrl().isBlank()
                ? "document://" + task.taskId()
                : section.sourceUrl();
        if (section.pageNumber() != null) {
            return base + (base.contains("#") ? "&" : "#") + "page=" + section.pageNumber();
        }
        return base;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
