package io.github.trustrag.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 EvalDatasetService 的关键行为、边界条件和回归场景。
 */
class EvalDatasetServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-20T12:00:00Z");

    private InMemoryEvaluationRepository repository;
    private EvalDatasetService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEvaluationRepository();
        service = new EvalDatasetService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void updatesMutableFieldsAndPreservesCreationMetadata() {
        EvalDataset original = repository.saveDataset(new EvalDataset(
                null, "Baseline", "old", "tenant-a", "project-a", "alice",
                true, Instant.EPOCH, Instant.EPOCH));

        EvalDataset updated = service.update(
                original.id(), "Regression", "new", "tenant-b", "project-b", false);

        assertThat(updated.name()).isEqualTo("Regression");
        assertThat(updated.description()).isEqualTo("new");
        assertThat(updated.tenantId()).isEqualTo("tenant-b");
        assertThat(updated.projectId()).isEqualTo("project-b");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.createdBy()).isEqualTo("alice");
        assertThat(updated.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesToDeleteDatasetWithHistoricalRuns() {
        EvalDataset dataset = repository.saveDataset(new EvalDataset(
                null, "Baseline", null, null, null, null,
                true, Instant.EPOCH, Instant.EPOCH));
        repository.createRun(new EvalRun(
                null, dataset.id(), "run", EvalRunType.MANUAL, BeforeAfterGroup.NORMAL,
                EvalRunStatus.SUCCEEDED, 0, 0, 0, null, null, Instant.EPOCH,
                Instant.EPOCH, Instant.EPOCH, null, null, Instant.EPOCH));

        assertThatThrownBy(() -> service.delete(dataset.id()))
                .isInstanceOf(EvaluationResourceConflictException.class)
                .hasMessageContaining("historical runs");
    }

    @Test
    void deletesDatasetWithoutHistoricalRuns() {
        EvalDataset dataset = repository.saveDataset(new EvalDataset(
                null, "Disposable", null, null, null, null,
                true, Instant.EPOCH, Instant.EPOCH));

        service.delete(dataset.id());

        assertThat(repository.findDataset(dataset.id())).isEmpty();
    }
}
