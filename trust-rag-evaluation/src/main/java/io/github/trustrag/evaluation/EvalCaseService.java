package io.github.trustrag.evaluation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * EvalCaseService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class EvalCaseService {

    private final EvaluationRepository repository;
    private final Clock clock;

    public EvalCaseService(EvaluationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EvalCase create(EvalCase evalCase, List<ExpectedKnowledge> expectedKnowledge) {
        EvalCase value = new EvalCase(
                null,
                evalCase.datasetId(),
                evalCase.question(),
                evalCase.expectedAnswer(),
                evalCase.tenantId(),
                evalCase.projectId(),
                evalCase.userId(),
                evalCase.conversationId(),
                evalCase.tags(),
                evalCase.difficulty(),
                evalCase.enabled(),
                clock.instant(),
                clock.instant());
        return repository.saveCaseWithExpectedKnowledge(value, expectedKnowledge);
    }

    public List<EvalCase> list(long datasetId, boolean onlyEnabled, int limit, int offset) {
        return repository.listCases(datasetId, onlyEnabled, limit, offset);
    }

    public EvalCase get(long evalCaseId) {
        return repository.findCase(evalCaseId)
                .orElseThrow(() -> new IllegalArgumentException("eval case not found: " + evalCaseId));
    }

    public EvalCase update(
            long evalCaseId,
            EvalCase changes,
            List<ExpectedKnowledge> expectedKnowledge) {
        EvalCase current = get(evalCaseId);
        if (changes.datasetId() != null && !current.datasetId().equals(changes.datasetId())) {
            throw new IllegalArgumentException("eval case cannot be moved to another dataset");
        }
        if (changes.question() == null || changes.question().isBlank()) {
            throw new IllegalArgumentException("eval case question must not be blank");
        }
        EvalCase value = new EvalCase(
                current.id(),
                current.datasetId(),
                changes.question().trim(),
                changes.expectedAnswer(),
                changes.tenantId(),
                changes.projectId(),
                changes.userId(),
                changes.conversationId(),
                changes.tags(),
                changes.difficulty(),
                changes.enabled(),
                current.createdAt(),
                clock.instant());
        return repository.saveCaseWithExpectedKnowledge(value, expectedKnowledge);
    }

    public void delete(long evalCaseId) {
        get(evalCaseId);
        if (repository.caseHasResults(evalCaseId)) {
            throw new EvaluationResourceConflictException(
                    "eval case has historical results and cannot be deleted: " + evalCaseId);
        }
        repository.deleteCase(evalCaseId);
    }

    public List<ExpectedKnowledge> expectedKnowledge(long evalCaseId) {
        return repository.listExpectedKnowledge(evalCaseId);
    }

    public void replaceExpectedKnowledge(long evalCaseId, List<ExpectedKnowledge> expectedKnowledge) {
        repository.replaceExpectedKnowledge(evalCaseId, withCaseId(evalCaseId, expectedKnowledge));
    }

    private List<ExpectedKnowledge> withCaseId(long evalCaseId, List<ExpectedKnowledge> expectedKnowledge) {
        if (expectedKnowledge == null) {
            return List.of();
        }
        List<ExpectedKnowledge> result = new ArrayList<>();
        for (ExpectedKnowledge item : expectedKnowledge) {
            result.add(new ExpectedKnowledge(
                    item.id(), evalCaseId, item.knowledgeId(),
                    item.relevanceGrade(), item.createdAt() == null ? clock.instant() : item.createdAt()));
        }
        return result;
    }
}
