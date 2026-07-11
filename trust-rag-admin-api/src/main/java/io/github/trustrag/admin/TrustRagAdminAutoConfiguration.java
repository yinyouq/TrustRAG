package io.github.trustrag.admin;

import io.github.trustrag.core.service.CandidateKnowledgeService;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.PromotionTaskService;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.PrivacyFilter;
import io.github.trustrag.document.DocumentImportService;
import io.github.trustrag.evaluation.EvalCaseService;
import io.github.trustrag.evaluation.EvalCaseCsvImportService;
import io.github.trustrag.evaluation.EvalDatasetService;
import io.github.trustrag.evaluation.EvalReportService;
import io.github.trustrag.evaluation.EvalRunService;
import io.github.trustrag.evaluation.EvaluationRepository;
import io.github.trustrag.evaluation.GovernanceMetricService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 自动装配入口，按配置创建 TrustRAG 默认 Bean。
 */
@AutoConfiguration(afterName = "io.github.trustrag.starter.TrustRagAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "trust-rag.admin-api", name = "enabled", havingValue = "true")
public class TrustRagAdminAutoConfiguration {

    @Bean
    @ConditionalOnBean(TrustRagFeedbackService.class)
    TrustRagFeedbackController trustRagFeedbackController(TrustRagFeedbackService service) {
        return new TrustRagFeedbackController(service);
    }

    @Bean
    @ConditionalOnBean({KnowledgeReviewService.class, KnowledgeIngestionService.class, CandidateKnowledgeService.class})
    TrustRagKnowledgeController trustRagKnowledgeController(
            KnowledgeReviewService reviewService,
            KnowledgeIngestionService ingestionService,
            CandidateKnowledgeService candidateService,
            PrivacyFilter privacyFilter) {
        return new TrustRagKnowledgeController(
                reviewService, ingestionService, candidateService, privacyFilter);
    }

    @Bean
    @ConditionalOnBean({DocumentImportService.class, KnowledgeRepository.class})
    TrustRagDocumentController trustRagDocumentController(
            DocumentImportService service,
            KnowledgeRepository knowledgeRepository) {
        return new TrustRagDocumentController(service, knowledgeRepository);
    }

    @Bean
    @ConditionalOnBean({EvalDatasetService.class, EvalCaseService.class})
    @ConditionalOnMissingBean
    EvalCaseCsvImportService evalCaseCsvImportService(
            EvalDatasetService datasetService,
            EvalCaseService caseService) {
        return new EvalCaseCsvImportService(datasetService, caseService);
    }

    @Bean
    @ConditionalOnBean({
            EvalDatasetService.class,
            EvalCaseService.class,
            EvalCaseCsvImportService.class,
            EvalRunService.class,
            EvalReportService.class,
            GovernanceMetricService.class,
            EvaluationRepository.class})
    TrustRagEvaluationController trustRagEvaluationController(
            EvalDatasetService datasetService,
            EvalCaseService caseService,
            EvalCaseCsvImportService csvImportService,
            EvalRunService runService,
            EvalReportService reportService,
            GovernanceMetricService governanceService,
            EvaluationRepository repository) {
        return new TrustRagEvaluationController(
                datasetService, caseService, csvImportService, runService, reportService,
                governanceService, repository);
    }

    @Bean
    @ConditionalOnBean({
            PromotionTaskService.class,
            KnowledgePromotionWorker.class,
            ConflictRecordRepository.class,
            KnowledgeRepository.class,
            KnowledgeLifecycleManager.class})
    TrustRagGovernanceController trustRagGovernanceController(
            PromotionTaskService taskService,
            KnowledgePromotionWorker worker,
            ConflictRecordRepository conflictRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeLifecycleManager lifecycleManager) {
        return new TrustRagGovernanceController(
                taskService, worker, conflictRepository,
                knowledgeRepository, lifecycleManager);
    }

    @Bean
    TrustRagExceptionHandler trustRagExceptionHandler() {
        return new TrustRagExceptionHandler();
    }
}
