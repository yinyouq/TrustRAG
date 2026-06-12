package io.github.trustrag.admin;

import io.github.trustrag.core.service.CandidateKnowledgeService;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import io.github.trustrag.core.spi.PrivacyFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

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
    TrustRagExceptionHandler trustRagExceptionHandler() {
        return new TrustRagExceptionHandler();
    }
}
