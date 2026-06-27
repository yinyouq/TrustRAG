package io.github.trustrag.admin;

import io.github.trustrag.core.model.ConflictRecord;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PromotionTask;
import io.github.trustrag.core.model.PromotionTaskStatus;
import io.github.trustrag.core.model.PromotionTaskType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.PromotionTaskService;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TrustRagGovernanceController 的 REST 接口入口，负责把 HTTP 请求转换为领域服务调用。
 */
@RestController
@RequestMapping("/trust-rag/admin")
public final class TrustRagGovernanceController {

    private final PromotionTaskService taskService;
    private final KnowledgePromotionWorker promotionWorker;
    private final ConflictRecordRepository conflictRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeLifecycleManager lifecycleManager;

    public TrustRagGovernanceController(
            PromotionTaskService taskService,
            KnowledgePromotionWorker promotionWorker,
            ConflictRecordRepository conflictRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeLifecycleManager lifecycleManager) {
        this.taskService = taskService;
        this.promotionWorker = promotionWorker;
        this.conflictRepository = conflictRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.lifecycleManager = lifecycleManager;
    }

    @PostMapping("/promotion/run")
    public PromotionRunResponse runPromotion(
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        int created = taskService.scanAndCreate(safeLimit);
        int processed = promotionWorker.runBatch(safeLimit);
        return new PromotionRunResponse(created, processed);
    }

    @GetMapping("/promotion/tasks")
    public List<PromotionTask> promotionTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long knowledgeId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return taskService.find(
                enumValue(PromotionTaskStatus.class, status),
                enumValue(PromotionTaskType.class, taskType),
                knowledgeId, limit, offset);
    }

    @GetMapping("/conflicts")
    public List<ConflictRecord> conflicts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return conflictRepository.find(
                Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
    }

    @GetMapping("/knowledge/medium")
    public List<KnowledgeItem> mediumKnowledge(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return knowledgeRepository.findByTrustAndStatuses(
                TrustLevel.MEDIUM,
                Set.of(KnowledgeStatus.MEDIUM_ENABLED, KnowledgeStatus.HUMAN_REVIEW_PENDING),
                Math.min(Math.max(limit, 1), 200),
                Math.max(offset, 0));
    }

    @GetMapping("/knowledge")
    public List<KnowledgeItem> knowledge(
            @RequestParam(required = false) String trustLevel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        TrustLevel parsedTrust = enumValue(TrustLevel.class, trustLevel);
        KnowledgeStatus parsedStatus = enumValue(KnowledgeStatus.class, status);
        if (parsedTrust == null) {
            parsedTrust = TrustLevel.MEDIUM;
        }
        Set<KnowledgeStatus> statuses = parsedStatus == null
                ? defaultStatuses(parsedTrust)
                : Set.of(parsedStatus);
        return knowledgeRepository.findByTrustAndStatuses(
                parsedTrust, statuses,
                Math.min(Math.max(limit, 1), 200),
                Math.max(offset, 0));
    }

    @PostMapping("/knowledge/{id}/downgrade")
    public KnowledgeItem downgrade(
            @PathVariable long id,
            @Valid @RequestBody LifecycleRequest request) {
        return lifecycleManager.downgrade(id, request.operatorId(), request.reason());
    }

    @PostMapping("/knowledge/{id}/rollback")
    public KnowledgeItem rollback(
            @PathVariable long id,
            @Valid @RequestBody LifecycleRequest request) {
        return lifecycleManager.rollback(id, request.operatorId(), request.reason());
    }

    @PostMapping("/knowledge/{id}/merge")
    public KnowledgeItem merge(
            @PathVariable long id,
            @Valid @RequestBody MergeRequest request) {
        return lifecycleManager.merge(request.sourceKnowledgeIds(), id, request.operatorId());
    }

    @PostMapping("/knowledge/merge")
    public KnowledgeItem merge(@Valid @RequestBody MergeToTargetRequest request) {
        return lifecycleManager.merge(
                request.sourceKnowledgeIds(), request.targetKnowledgeId(), request.operatorId());
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null || value.isBlank()
                ? null
                : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }

    private Set<KnowledgeStatus> defaultStatuses(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case HIGH -> Set.of(KnowledgeStatus.HIGH_ENABLED);
            case MEDIUM -> Set.of(
                    KnowledgeStatus.MEDIUM_ENABLED,
                    KnowledgeStatus.HUMAN_REVIEW_PENDING);
            case LOW -> Set.of(
                    KnowledgeStatus.LOW_PENDING,
                    KnowledgeStatus.LOW_ENABLED,
                    KnowledgeStatus.PROMOTION_PENDING,
                    KnowledgeStatus.PROMOTION_RUNNING);
        };
    }

    public record PromotionRunResponse(int createdTasks, int processedTasks) {
    }

    public record LifecycleRequest(@NotBlank String operatorId, @NotBlank String reason) {
    }

    public record MergeRequest(
            @NotBlank String operatorId,
            @NotEmpty List<Long> sourceKnowledgeIds) {
    }

    public record MergeToTargetRequest(
            @NotBlank String operatorId,
            long targetKnowledgeId,
            @NotEmpty List<Long> sourceKnowledgeIds) {
    }
}
