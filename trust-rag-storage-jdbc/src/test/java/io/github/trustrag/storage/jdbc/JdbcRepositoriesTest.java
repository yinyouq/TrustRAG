package io.github.trustrag.storage.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.IndexOperation;
import io.github.trustrag.core.model.IndexSyncTask;
import io.github.trustrag.core.model.IndexSyncTaskStatus;
import io.github.trustrag.core.model.IndexTarget;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.SearchType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.RetrievedChunk;
import io.github.trustrag.core.model.KnowledgeGovernance;
import io.github.trustrag.core.model.KnowledgeSourceMetadata;
import io.github.trustrag.document.DocumentImportOptions;
import io.github.trustrag.document.DocumentImportStatus;
import io.github.trustrag.document.DocumentImportTask;
import io.github.trustrag.evaluation.BeforeAfterGroup;
import io.github.trustrag.evaluation.EvalCase;
import io.github.trustrag.evaluation.EvalDataset;
import io.github.trustrag.evaluation.EvalReport;
import io.github.trustrag.evaluation.EvalResult;
import io.github.trustrag.evaluation.EvalResultStatus;
import io.github.trustrag.evaluation.EvalRun;
import io.github.trustrag.evaluation.EvalRunStatus;
import io.github.trustrag.evaluation.EvalRunType;
import io.github.trustrag.evaluation.ExpectedKnowledge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 JdbcRepositories 的关键行为、边界条件和回归场景。
 */
class JdbcRepositoriesTest {

    private EmbeddedDatabase database;
    private JdbcKnowledgeRepository knowledgeRepository;
    private JdbcRagTraceRepository traceRepository;
    private JdbcIndexSyncTaskRepository indexSyncTaskRepository;
    private JdbcDocumentImportTaskRepository documentImportTaskRepository;
    private JdbcEvaluationRepository evaluationRepository;
    private JdbcEvalTraceReader evalTraceReader;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema-h2.sql")
                .build();
        jdbc = new NamedParameterJdbcTemplate(database);
        knowledgeRepository = new JdbcKnowledgeRepository(jdbc);
        traceRepository = new JdbcRagTraceRepository(jdbc, new ObjectMapper());
        indexSyncTaskRepository = new JdbcIndexSyncTaskRepository(jdbc);
        documentImportTaskRepository = new JdbcDocumentImportTaskRepository(jdbc);
        evaluationRepository = new JdbcEvaluationRepository(jdbc, new ObjectMapper());
        evalTraceReader = new JdbcEvalTraceReader(jdbc);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void persistsKnowledgeAndTrace() {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        KnowledgeItem saved = knowledgeRepository.save(new KnowledgeItem(
                null, "title", "claim", "content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.INDEXING, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-1",
                null, null, null, now, now, null,
                KnowledgeGovernance.empty(),
                new KnowledgeSourceMetadata(
                        "Source title", "https://example.test/doc", 3,
                        "Chapter / Section", "document-1", 0)));

        assertThat(saved.id()).isPositive();
        assertThat(knowledgeRepository.findById(saved.id())).contains(saved);

        KnowledgeItem enabled = saved.withIndexState(
                KnowledgeStatus.HIGH_ENABLED, saved.id().toString(), "test-model", 3, now);
        assertThat(knowledgeRepository.updateIfState(
                enabled, KnowledgeStatus.INDEXING, saved.version())).isTrue();
        assertThat(knowledgeRepository.updateIfState(
                enabled, KnowledgeStatus.INDEXING, saved.version())).isFalse();

        RagTrace trace = RagTrace.start(RagRequest.builder().question("question").build(), now);
        RetrievedChunk chunk = new RetrievedChunk(
                saved.id(), "title", "content", "source",
                TrustLevel.HIGH, ScopeType.GLOBAL,
                0.91, 1, 12.5, 2, 1.0 / 61.0 + 1.0 / 62.0,
                SearchType.HYBRID_RRF, null, 1.0, 0.0325, true);
        trace.rewrittenQueries(List.of("question"));
        trace.retrievedChunks(List.of(chunk));
        trace.usedChunks(List.of(chunk));
        trace.complete(new LlmResponse("answer", 0.8, new TokenUsage(2, 3)), GapDetectionResult.noGap(), 12);
        traceRepository.save(trace);

        assertThat(traceRepository.existsByTraceId(trace.traceId())).isTrue();
        assertThat(traceRepository.findQuestionByTraceId(trace.traceId())).contains("question");
        Map<String, Object> retrievalLog = jdbc.queryForMap(
                "SELECT * FROM retrieval_log WHERE trace_id=:traceId",
                Map.of("traceId", trace.traceId()));
        assertThat(retrievalLog.get("search_type")).isEqualTo("HYBRID_RRF");
        assertThat(retrievalLog.get("vector_rank")).isEqualTo(1);
        assertThat(retrievalLog.get("keyword_rank")).isEqualTo(2);
    }

    @Test
    void persistsEvaluationObjectsAndReadsTraceRankings() {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        KnowledgeItem knowledge = knowledgeRepository.save(new KnowledgeItem(
                null, "eval title", "eval claim", "eval content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, "project-1", "tenant-1", "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-eval",
                null, null, null, now, now, null));
        RagTrace trace = RagTrace.start(RagRequest.builder()
                .question("eval question")
                .tenantId("tenant-1")
                .projectId("project-1")
                .evaluationMode(true)
                .evalRunId(100L)
                .evalCaseId(200L)
                .build(), now);
        RetrievedChunk chunk = new RetrievedChunk(
                knowledge.id(), knowledge.title(), knowledge.content(), knowledge.sourceRef(),
                knowledge.trustLevel(), knowledge.scopeType(),
                0.9, null, 1.0, 0.9, true);
        trace.rewrittenQueries(List.of("eval question"));
        trace.retrievedChunks(List.of(chunk));
        trace.usedChunks(List.of(chunk));
        trace.complete(new LlmResponse("eval answer", 0.9, new TokenUsage(1, 2)), GapDetectionResult.noGap(), 8);
        traceRepository.save(trace);

        EvalDataset dataset = evaluationRepository.saveDataset(new EvalDataset(
                null, "dataset", "desc", "tenant-1", "project-1", "tester",
                true, now, now));
        EvalCase evalCase = evaluationRepository.saveCase(new EvalCase(
                null, dataset.id(), "eval question", "eval answer",
                "tenant-1", "project-1", null, null, List.of("smoke"),
                "easy", true, now, now));
        evaluationRepository.replaceExpectedKnowledge(
                evalCase.id(),
                List.of(new ExpectedKnowledge(null, evalCase.id(), knowledge.id(), 3, now)));
        EvalRun run = evaluationRepository.createRun(new EvalRun(
                null, dataset.id(), "run", EvalRunType.MANUAL, BeforeAfterGroup.NORMAL,
                EvalRunStatus.PENDING, 1, 0, 0, "{}", "{}", now,
                null, null, null, "tester", now));
        evaluationRepository.markRunRunning(run.id(), now);
        EvalResult result = evaluationRepository.saveResult(new EvalResult(
                null, run.id(), evalCase.id(), trace.traceId(), "eval question", "eval answer",
                "eval answer", 1, 1, 1.0, 1.0, 0.2, 0.1, 1.0,
                1.0, 1.0, null, null, null, null,
                1, 2, 8, EvalResultStatus.SUCCEEDED, null, now));
        EvalReport report = evaluationRepository.saveReport(new EvalReport(
                null, run.id(), dataset.id(), 1, 1, 0, 1.0, 1.0,
                0.2, 0.1, 1.0, 1.0, 1.0, null, null, null,
                null, 8.0, 12.0, 8.0, 12.0, 8.0, 12.0, 8.0, 12.0, "{}", now));

        assertThat(evaluationRepository.findDataset(dataset.id())).contains(dataset);
        assertThat(evaluationRepository.listCases(dataset.id(), true, 10, 0)).containsExactly(evalCase);
        assertThat(evaluationRepository.listExpectedKnowledge(evalCase.id()))
                .singleElement()
                .extracting(ExpectedKnowledge::knowledgeId)
                .isEqualTo(knowledge.id());
        assertThat(evaluationRepository.listResults(run.id(), 10, 0)).containsExactly(result);
        assertThat(evaluationRepository.findReportByRunId(run.id())).contains(report);
        assertThat(evalTraceReader.findRetrievedKnowledgeIds(trace.traceId())).containsExactly(knowledge.id());
    }

    @Test
    void fillsCreatedAtForExpectedKnowledgeWithoutTimestamp() {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        KnowledgeItem first = knowledgeRepository.save(new KnowledgeItem(
                null, "expected title 1", "expected claim 1", "expected content 1", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-expected-null-1",
                null, null, null, now, now, null));
        KnowledgeItem second = knowledgeRepository.save(new KnowledgeItem(
                null, "expected title 2", "expected claim 2", "expected content 2", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-expected-null-2",
                null, null, null, now, now, null));
        EvalDataset dataset = evaluationRepository.saveDataset(new EvalDataset(
                null, "dataset", null, null, null, null, true, now, now));
        EvalCase evalCase = evaluationRepository.saveCase(new EvalCase(
                null, dataset.id(), "question", "answer",
                null, null, null, null, List.of(), null, true, now, now));

        evaluationRepository.replaceExpectedKnowledge(
                evalCase.id(),
                List.of(
                        new ExpectedKnowledge(null, evalCase.id(), first.id(), 1, null),
                        new ExpectedKnowledge(null, evalCase.id(), second.id(), 1, null)));

        assertThat(evaluationRepository.listExpectedKnowledge(evalCase.id()))
                .hasSize(2)
                .allSatisfy(expected -> assertThat(expected.createdAt()).isNotNull())
                .extracting(ExpectedKnowledge::knowledgeId)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void rollsBackCaseAndExpectedKnowledgeAsOneUnit() {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        KnowledgeItem knowledge = knowledgeRepository.save(new KnowledgeItem(
                null, "eval title", "eval claim", "eval content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-atomic-eval",
                null, null, null, now, now, null));
        EvalDataset dataset = evaluationRepository.saveDataset(new EvalDataset(
                null, "dataset", null, null, null, null, true, now, now));
        EvalCase original = evaluationRepository.saveCaseWithExpectedKnowledge(
                new EvalCase(null, dataset.id(), "original question", null,
                        null, null, null, null, List.of(), null, true, now, now),
                List.of(new ExpectedKnowledge(null, null, knowledge.id(), 1, now)));

        EvalCase changed = new EvalCase(original.id(), dataset.id(), "changed question", null,
                null, null, null, null, List.of(), null, true, now, now.plusSeconds(1));

        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(database));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                evaluationRepository.saveCaseWithExpectedKnowledge(
                        changed,
                        List.of(new ExpectedKnowledge(
                                null, original.id(), Long.MAX_VALUE, 1, now)))))
                .isInstanceOf(RuntimeException.class);

        assertThat(evaluationRepository.findCase(original.id()))
                .get()
                .extracting(EvalCase::question)
                .isEqualTo("original question");
        assertThat(evaluationRepository.listExpectedKnowledge(original.id()))
                .singleElement()
                .extracting(ExpectedKnowledge::knowledgeId)
                .isEqualTo(knowledge.id());
    }

    @Test
    void persistsAndClaimsDocumentImportTask() {
        Instant now = Instant.parse("2026-06-13T00:00:00Z");
        DocumentImportTask task = DocumentImportTask.pendingUpload(
                "guide.pdf",
                "application/pdf",
                "D:/data/guide.pdf",
                new DocumentImportOptions(
                        "Guide", "official_doc", TrustLevel.HIGH, ScopeType.GLOBAL,
                        null, null, "project-1", "tenant-1"),
                now);

        DocumentImportTask saved = documentImportTaskRepository.save(task);

        assertThat(saved.id()).isPositive();
        assertThat(documentImportTaskRepository.list(10, 0))
                .extracting(DocumentImportTask::taskId)
                .contains(saved.taskId());
        assertThat(documentImportTaskRepository.findByTaskId(saved.taskId()))
                .contains(saved);
        KnowledgeItem knowledge = knowledgeRepository.save(new KnowledgeItem(
                null, "Guide / Section", null, "guide content", null, "document_chunk",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, "project-1", "tenant-1", "official_doc", "document://" + saved.taskId(),
                "document://" + saved.taskId(), null, null, null, 1.0, 0.0, 1, "hash-doc-task-knowledge",
                null, null, null, now, now, null,
                KnowledgeGovernance.empty(),
                new KnowledgeSourceMetadata("Guide", null, 1, "Intro", saved.taskId(), 0)));
        assertThat(knowledgeRepository.findByDocumentId(saved.taskId(), 10, 0))
                .singleElement()
                .extracting(KnowledgeItem::id)
                .isEqualTo(knowledge.id());
        assertThat(knowledgeRepository.countByDocumentId(saved.taskId())).isEqualTo(1);
        assertThat(documentImportTaskRepository.findRunnable(3, 10))
                .singleElement()
                .extracting(DocumentImportTask::status)
                .isEqualTo(DocumentImportStatus.PENDING);
        assertThat(documentImportTaskRepository.claim(saved, now.plusSeconds(1))).isTrue();
        assertThat(documentImportTaskRepository.claim(saved, now.plusSeconds(2))).isFalse();
    }

    @Test
    void persistsLargeKeywordScoreAndLeavesVectorScoreNullForKeywordOnlyRetrieval() {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        KnowledgeItem saved = knowledgeRepository.save(new KnowledgeItem(
                null, "keyword title", "keyword claim", "keyword content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "keyword-source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-keyword-only",
                null, null, null, now, now, null));
        RagTrace trace = RagTrace.start(
                RagRequest.builder().question("keyword question").build(), now);
        RetrievedChunk chunk = new RetrievedChunk(
                saved.id(), saved.title(), saved.content(), saved.sourceRef(),
                saved.trustLevel(), saved.scopeType(),
                0.0, null, 123.456, 1, 1.0 / 61.0,
                SearchType.KEYWORD_ONLY, null, 1.0, 1.0 / 61.0, true);
        trace.rewrittenQueries(List.of("keyword question"));
        trace.retrievedChunks(List.of(chunk));
        trace.usedChunks(List.of(chunk));
        trace.complete(
                new LlmResponse("answer", 0.8, new TokenUsage(2, 3)),
                GapDetectionResult.noGap(),
                12);

        traceRepository.save(trace);

        Map<String, Object> retrievalLog = jdbc.queryForMap(
                "SELECT vector_score, keyword_score, search_type "
                        + "FROM retrieval_log WHERE trace_id=:traceId",
                Map.of("traceId", trace.traceId()));
        assertThat(retrievalLog.get("vector_score")).isNull();
        assertThat(retrievalLog.get("keyword_score")).isEqualTo(new java.math.BigDecimal("123.456000"));
        assertThat(retrievalLog.get("search_type")).isEqualTo("KEYWORD_ONLY");
    }

    @Test
    void persistsAndClaimsIndexSyncTask() {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        KnowledgeItem knowledge = knowledgeRepository.save(new KnowledgeItem(
                null, "title", "claim", "content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.INDEX_FAILED, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-sync",
                null, null, null, now, now, null));
        IndexSyncTask saved = indexSyncTaskRepository.save(IndexSyncTask.pending(
                knowledge.id(), IndexTarget.OPENSEARCH, IndexOperation.UPSERT,
                KnowledgeStatus.HIGH_ENABLED, "unavailable", now));

        assertThat(indexSyncTaskRepository.findRunnable(3, 10))
                .singleElement()
                .extracting(IndexSyncTask::status)
                .isEqualTo(IndexSyncTaskStatus.PENDING);
        assertThat(indexSyncTaskRepository.claim(saved, now)).isTrue();

        IndexSyncTask succeeded = saved.start(now).succeed(now);
        indexSyncTaskRepository.update(succeeded);

        assertThat(indexSyncTaskRepository.findActiveByKnowledgeId(knowledge.id()))
                .isEmpty();
    }
}
