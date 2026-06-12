package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.TransactionRunner;

import java.time.Clock;
import java.util.List;

public final class PromotionTaskService {

    private final KnowledgeRepository knowledgeRepository;
    private final PromotionTaskRepository taskRepository;
    private final KnowledgeStateMachine stateMachine;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public PromotionTaskService(
            KnowledgeRepository knowledgeRepository,
            PromotionTaskRepository taskRepository,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.knowledgeRepository = knowledgeRepository;
        this.taskRepository = taskRepository;
        this.stateMachine = stateMachine;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public int scanAndCreate(int limit) {
        int created = 0;
        for (KnowledgeItem item : knowledgeRepository.findPromotionCandidates(limit)) {
            if (taskRepository.findActiveByKnowledgeId(
                    item.id(), PromotionTaskType.LOW_TO_MEDIUM).isPresent()) {
                continue;
            }
            transactionRunner.required(() -> {
                KnowledgeItem current = knowledgeRepository.findById(item.id()).orElseThrow();
                if (current.status() == KnowledgeStatus.LOW_ENABLED
                        || current.status() == KnowledgeStatus.LOW_PENDING) {
                    stateMachine.validate(current.status(), KnowledgeStatus.PROMOTION_PENDING);
                    KnowledgeItem pending = current.withStatus(
                            KnowledgeStatus.PROMOTION_PENDING, clock.instant());
                    knowledgeRepository.updateIfState(pending, current.status(), current.version());
                }
                taskRepository.save(PromotionTask.pending(
                        item.id(), PromotionTaskType.LOW_TO_MEDIUM, clock.instant()));
            });
            created++;
        }
        return created;
    }

    public List<PromotionTask> find(
            PromotionTaskStatus status,
            PromotionTaskType type,
            Long knowledgeId,
            int limit,
            int offset) {
        return taskRepository.find(
                status, type, knowledgeId,
                Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
    }
}
