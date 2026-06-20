package io.github.trustrag.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCaseCsvImportServiceTest {

    private InMemoryEvaluationRepository repository;
    private EvalCaseCsvImportService importer;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEvaluationRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);
        EvalDatasetService datasetService = new EvalDatasetService(repository, clock);
        EvalCaseService caseService = new EvalCaseService(repository, clock);
        EvalDataset dataset = datasetService.create(
                "CSV dataset", null, "tenant-default", "project-default", "tester", true);
        importer = new EvalCaseCsvImportService(datasetService, caseService);
        assertThat(dataset.id()).isEqualTo(1L);
    }

    @Test
    void importsQuotedContentTagsAndExpectedKnowledge() {
        String csv = "\uFEFFquestion,expected_answer,expected_knowledge_ids,tags,difficulty\n"
                + "\"What is RAG, exactly?\",\"Retrieval, then generation\",12|13,rag|core,HARD\n";

        EvalCaseCsvImportResult result = importer.importCsv(
                1L, new StringReader(csv), EvalCaseCsvImportService.ImportDefaults.empty());

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.failedRows()).isZero();
        EvalCase saved = repository.listCases(1L, false, 10, 0).get(0);
        assertThat(saved.question()).isEqualTo("What is RAG, exactly?");
        assertThat(saved.tags()).containsExactly("rag", "core");
        assertThat(saved.difficulty()).isEqualTo("HARD");
        assertThat(repository.listExpectedKnowledge(saved.id()))
                .extracting(ExpectedKnowledge::knowledgeId)
                .containsExactly(12L, 13L);
    }

    @Test
    void keepsValidRowsWhenAnotherRowIsInvalid() {
        String csv = "question,expected_knowledge_ids,enabled\n"
                + "valid question,42,true\n"
                + "broken question,not-a-number,true\n"
                + ",43,true\n";

        EvalCaseCsvImportResult result = importer.importCsv(
                1L, new StringReader(csv),
                new EvalCaseCsvImportService.ImportDefaults(
                        "tenant", "project", "user", "conversation"));

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(2);
        assertThat(result.errors())
                .extracting(EvalCaseCsvImportResult.RowError::rowNumber)
                .containsExactly(3L, 4L);
        assertThat(repository.listCases(1L, false, 10, 0)).hasSize(1);
    }

    @Test
    void rejectsUnsupportedDifficultyAndInvalidBoolean() {
        String csv = "question,difficulty,enabled\n"
                + "question one,EXTREME,true\n"
                + "question two,EASY,sometimes\n";

        EvalCaseCsvImportResult result = importer.importCsv(
                1L, new StringReader(csv), EvalCaseCsvImportService.ImportDefaults.empty());

        assertThat(result.importedRows()).isZero();
        assertThat(result.errors())
                .extracting(EvalCaseCsvImportResult.RowError::message)
                .anyMatch(message -> message.contains("difficulty"))
                .anyMatch(message -> message.contains("enabled"));
    }
}
