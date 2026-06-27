package io.github.trustrag.evaluation;

import java.time.Clock;
import java.util.List;

/**
 * EvalDatasetService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class EvalDatasetService {

    private final EvaluationRepository repository;
    private final Clock clock;

    public EvalDatasetService(EvaluationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EvalDataset create(
            String name,
            String description,
            String tenantId,
            String projectId,
            String createdBy,
            boolean enabled) {
        return repository.saveDataset(new EvalDataset(
                null, name, description, tenantId, projectId, createdBy,
                enabled, clock.instant(), clock.instant()));
    }

    public List<EvalDataset> list(String tenantId, String projectId, boolean includeDisabled, int limit, int offset) {
        return repository.listDatasets(tenantId, projectId, includeDisabled, limit, offset);
    }

    public EvalDataset get(long id) {
        return repository.findDataset(id)
                .orElseThrow(() -> new IllegalArgumentException("eval dataset not found: " + id));
    }

    public EvalDataset update(
            long id,
            String name,
            String description,
            String tenantId,
            String projectId,
            boolean enabled) {
        EvalDataset current = get(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("eval dataset name must not be blank");
        }
        return repository.saveDataset(new EvalDataset(
                current.id(),
                name.trim(),
                description,
                tenantId,
                projectId,
                current.createdBy(),
                enabled,
                current.createdAt(),
                clock.instant()));
    }

    public void delete(long id) {
        get(id);
        if (repository.datasetHasRuns(id)) {
            throw new EvaluationResourceConflictException(
                    "eval dataset has historical runs and cannot be deleted: " + id);
        }
        repository.deleteDataset(id);
    }
}
