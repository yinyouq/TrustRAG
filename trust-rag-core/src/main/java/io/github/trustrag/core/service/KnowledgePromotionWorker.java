package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.model.PromotionResult;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.spi.KnowledgePromotionEngine;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.PromotionTaskRepository;

import java.time.Clock;
import java.util.List;

public final class KnowledgePromotionWorker {

    private final PromotionTaskRepository taskRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgePromotionEngine engine;
    private final int retryLimit;
    private final Clock clock;

    public KnowledgePromotionWorker(
            PromotionTaskRepository taskRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgePromotionEngine engine,
            int retryLimit,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.engine = engine;
        this.retryLimit = retryLimit;
        this.clock = clock;
    }

    public int runBatch(int limit) {
        List<PromotionTask> tasks = taskRepository.findRunnable(retryLimit, limit);
        for (PromotionTask task : tasks) {
            process(task);
        }
        return tasks.size();
    }

    public void process(PromotionTask task) {
        if (!taskRepository.claim(task, clock.instant())) {
            return;
        }
        PromotionTask running = task.start(clock.instant());
        try {
            PromotionResult result = engine.evaluate(task.knowledgeId());
            apply(result);
            taskRepository.update(running.succeed(clock.instant()));
        } catch (Exception exception) {
            taskRepository.update(running.fail(abbreviate(exception.getMessage()), clock.instant()));
            knowledgeRepository.findById(task.knowledgeId()).ifPresent(this::restoreForRetry);
        }
    }

    private void apply(PromotionResult result) {
        if (result.action() == PromotionAction.PROMOTE_TO_MEDIUM) {
            engine.promoteToMedium(result.knowledgeId(), result);
        } else if (result.action() == PromotionAction.REJECT) {
            engine.reject(result.knowledgeId(), result.reason());
        } else if (result.action() == PromotionAction.MARK_CONFLICT) {
            engine.markConflict(result.knowledgeId(), result.reason());
        } else if (result.action() == PromotionAction.KEEP_LOW) {
            engine.keepLow(result.knowledgeId(), result.reason());
        } else if (result.action() == PromotionAction.MARK_EXPIRED) {
            engine.markExpired(result.knowledgeId(), result.reason());
        } else if (result.action() == PromotionAction.MERGE_PENDING) {
            engine.markMergePending(result.knowledgeId(), result.reason());
        } else {
            engine.keepLow(result.knowledgeId(), result.reason());
        }
    }

    private void restoreForRetry(KnowledgeItem item) {
        if (item.status() == KnowledgeStatus.PROMOTION_RUNNING) {
            KnowledgeItem pending = item.withStatus(
                    KnowledgeStatus.PROMOTION_PENDING, clock.instant());
            knowledgeRepository.updateIfState(pending, item.status(), item.version());
        }
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "Unknown promotion failure";
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
