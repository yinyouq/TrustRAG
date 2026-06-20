package io.github.trustrag.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-20T12:00:00Z");

    private InMemoryEvaluationRepository repository;
    private EvalCaseService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEvaluationRepository();
        service = new EvalCaseService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void updatesCaseAndExpectedKnowledge() {
        EvalCase original = repository.saveCase(evalCase(null, "old question", Instant.EPOCH));

        EvalCase updated = service.update(
                original.id(), evalCase(original.datasetId(), "new question", null),
                List.of(new ExpectedKnowledge(null, null, 99L, 3, null)));

        assertThat(updated.question()).isEqualTo("new question");
        assertThat(updated.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        assertThat(repository.listExpectedKnowledge(original.id()))
                .extracting(ExpectedKnowledge::knowledgeId)
                .containsExactly(99L);
    }

    @Test
    void refusesToMoveCaseToAnotherDataset() {
        EvalCase original = repository.saveCase(evalCase(null, "question", Instant.EPOCH));

        assertThatThrownBy(() -> service.update(
                original.id(), evalCase(999L, "question", null), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataset");
    }

    @Test
    void refusesToDeleteCaseWithHistoricalResults() {
        EvalCase original = repository.saveCase(evalCase(null, "question", Instant.EPOCH));
        repository.saveResult(new EvalResult(
                null, 20L, original.id(), null, original.question(), null, null,
                0, 0, null, null, null, null, null, null, null, null, null,
                null, null, 0, 0, 0, EvalResultStatus.SUCCEEDED, null, Instant.EPOCH));

        assertThatThrownBy(() -> service.delete(original.id()))
                .isInstanceOf(EvaluationResourceConflictException.class)
                .hasMessageContaining("historical results");
    }

    private EvalCase evalCase(Long datasetId, String question, Instant createdAt) {
        return new EvalCase(
                null, datasetId == null ? 1L : datasetId, question, "answer",
                "tenant", "project", "user", "conversation", List.of("tag"),
                "MEDIUM", true, createdAt, createdAt);
    }
}
