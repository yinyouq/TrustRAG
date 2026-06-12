package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.config.EngineOptions;
import io.github.trustrag.core.config.ConflictDetectionOptions;
import io.github.trustrag.core.config.DuplicateDetectionOptions;
import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.config.LifecycleOptions;
import io.github.trustrag.core.config.PromotionOptions;
import io.github.trustrag.core.config.RetrievalOptions;
import io.github.trustrag.core.config.SourceScoreOptions;
import io.github.trustrag.core.service.CandidateKnowledgeService;
import io.github.trustrag.core.service.DefaultCandidateExtractor;
import io.github.trustrag.core.service.DefaultConflictDetector;
import io.github.trustrag.core.service.DefaultDuplicateDetector;
import io.github.trustrag.core.service.DefaultEvidenceVerifier;
import io.github.trustrag.core.service.DefaultKnowledgeLifecycleManager;
import io.github.trustrag.core.service.DefaultKnowledgePromotionEngine;
import io.github.trustrag.core.service.DefaultPrivacyFilter;
import io.github.trustrag.core.service.DefaultPromptBuilder;
import io.github.trustrag.core.service.DefaultQueryRewriteService;
import io.github.trustrag.core.service.DefaultScopeClassifier;
import io.github.trustrag.core.service.DefaultScopeResolver;
import io.github.trustrag.core.service.DefaultTrustRagEngine;
import io.github.trustrag.core.service.DefaultTrustRagFeedbackService;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.KnowledgeStateMachine;
import io.github.trustrag.core.service.KnowledgeVisibilityPolicy;
import io.github.trustrag.core.service.NoOpRerankClient;
import io.github.trustrag.core.service.NoOpLlmPreReviewer;
import io.github.trustrag.core.service.PromotionTaskService;
import io.github.trustrag.core.service.RecursiveTextChunkStrategy;
import io.github.trustrag.core.service.RuleBasedKnowledgeRelationJudge;
import io.github.trustrag.core.service.RuleBasedKnowledgeGapDetector;
import io.github.trustrag.core.service.TrustAwareRetriever;
import io.github.trustrag.core.service.TrustRagEngine;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import io.github.trustrag.core.spi.CandidateExtractor;
import io.github.trustrag.core.spi.ChunkStrategy;
import io.github.trustrag.core.spi.ConflictDetector;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import io.github.trustrag.core.spi.DuplicateDetector;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.EvidenceVerifier;
import io.github.trustrag.core.spi.FeedbackRepository;
import io.github.trustrag.core.spi.KnowledgeGapDetector;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgePromotionEngine;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.KnowledgeRelationJudge;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.LlmClient;
import io.github.trustrag.core.spi.LlmPreReviewer;
import io.github.trustrag.core.spi.PrivacyFilter;
import io.github.trustrag.core.spi.PromptBuilder;
import io.github.trustrag.core.spi.PromptCustomizer;
import io.github.trustrag.core.spi.PromotionTaskRepository;
import io.github.trustrag.core.spi.QueryRewriteService;
import io.github.trustrag.core.spi.RagTraceRepository;
import io.github.trustrag.core.spi.RerankClient;
import io.github.trustrag.core.spi.ReviewCallback;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.ScopeClassifier;
import io.github.trustrag.core.spi.ScopeResolver;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.milvus.MilvusFilterBuilder;
import io.github.trustrag.milvus.MilvusKnowledgeVectorStore;
import io.github.trustrag.milvus.MilvusSettings;
import io.github.trustrag.storage.jdbc.JdbcFeedbackRepository;
import io.github.trustrag.storage.jdbc.JdbcConflictRecordRepository;
import io.github.trustrag.storage.jdbc.JdbcKnowledgeLineageRepository;
import io.github.trustrag.storage.jdbc.JdbcKnowledgeRepository;
import io.github.trustrag.storage.jdbc.JdbcPromotionTaskRepository;
import io.github.trustrag.storage.jdbc.JdbcRagTraceRepository;
import io.github.trustrag.storage.jdbc.JdbcReviewTaskRepository;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(TrustRagProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "trust-rag", name = "enabled", havingValue = "true")
public class TrustRagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock trustRagClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionRunner trustRagTransactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnMissingBean
    EngineOptions trustRagEngineOptions(TrustRagProperties properties) {
        TrustRagProperties.Engine engine = properties.getEngine();
        return new EngineOptions(
                engine.isEnableQueryRewrite(),
                engine.isEnableRerank(),
                engine.isEnableGapDetection(),
                engine.isEnableCandidateExtraction(),
                engine.getDefaultTopK(),
                engine.getPromptMaxChunks(),
                properties.getTrace().isSavePrompt());
    }

    @Bean
    @ConditionalOnMissingBean
    RetrievalOptions trustRagRetrievalOptions(TrustRagProperties properties) {
        TrustRagProperties.Retrieval retrieval = properties.getRetrieval();
        TrustRagProperties.TrustWeight weights = properties.getTrustWeight();
        return new RetrievalOptions(
                retrieval.getHighTrustTopK(),
                retrieval.getMediumTrustTopK(),
                retrieval.getLowTrustTopK(),
                retrieval.getMinVectorScore(),
                weights.getHigh(),
                weights.getMedium(),
                weights.getLowConversation(),
                weights.getLowUser(),
                weights.getLowProject(),
                weights.getLowTenant(),
                weights.getLowGlobalCandidate(),
                retrieval.isAllowGlobalLowCandidate());
    }

    @Bean
    @ConditionalOnMissingBean
    GapDetectionOptions trustRagGapDetectionOptions(TrustRagProperties properties) {
        TrustRagProperties.GapDetection gap = properties.getGapDetection();
        return new GapDetectionOptions(
                gap.getLowVectorScoreThreshold(),
                gap.getLowRerankScoreThreshold(),
                gap.getGapScoreThreshold(),
                gap.getUncertainExpressions());
    }

    @Bean
    @ConditionalOnMissingBean
    PromotionOptions trustRagPromotionOptions(TrustRagProperties properties) {
        TrustRagProperties.Promotion value = properties.getPromotion();
        return new PromotionOptions(
                value.isEnabled(), value.getBatchSize(), value.getRetryLimit(),
                value.getMinPromotionScore(), value.getMinSourceScore(),
                value.getMinEvidenceScore(), value.getMaxConflictRisk(),
                value.getMaxPrivacyRisk(), value.isLlmPreReviewEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    DuplicateDetectionOptions trustRagDuplicateDetectionOptions(TrustRagProperties properties) {
        TrustRagProperties.DuplicateDetection value = properties.getDuplicateDetection();
        return new DuplicateDetectionOptions(
                value.isEnabled(), value.isHashEnabled(),
                value.isVectorEnabled(), value.getSimilarityThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictDetectionOptions trustRagConflictDetectionOptions(TrustRagProperties properties) {
        TrustRagProperties.ConflictDetection value = properties.getConflictDetection();
        return new ConflictDetectionOptions(
                value.isEnabled(), value.isCompareWithHigh(), value.isCompareWithMedium(),
                value.getTopK(), value.getSimilarityThreshold(), value.isLlmJudgeEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    SourceScoreOptions trustRagSourceScoreOptions(TrustRagProperties properties) {
        return new SourceScoreOptions(properties.getSourceScore().getScores());
    }

    @Bean
    @ConditionalOnMissingBean
    LifecycleOptions trustRagLifecycleOptions(TrustRagProperties properties) {
        TrustRagProperties.Lifecycle value = properties.getLifecycle();
        return new LifecycleOptions(
                value.getLowTtlDays(), value.getMediumTtlDays(),
                value.isAutoExpireEnabled(), value.getNegativeFeedbackDowngradeThreshold(),
                value.getIndexFailedRetryLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeRepository knowledgeRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcKnowledgeRepository(
                new NamedParameterJdbcTemplate(dataSource), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    RagTraceRepository ragTraceRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcRagTraceRepository(new NamedParameterJdbcTemplate(dataSource), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    FeedbackRepository feedbackRepository(DataSource dataSource) {
        return new JdbcFeedbackRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    ReviewTaskRepository reviewTaskRepository(DataSource dataSource) {
        return new JdbcReviewTaskRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    PromotionTaskRepository promotionTaskRepository(DataSource dataSource) {
        return new JdbcPromotionTaskRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictRecordRepository conflictRecordRepository(DataSource dataSource) {
        return new JdbcConflictRecordRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeLineageRepository knowledgeLineageRepository(DataSource dataSource) {
        return new JdbcKnowledgeLineageRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnClass(ChatModel.class)
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    LlmClient springAiLlmClient(ChatModel chatModel) {
        return new SpringAiLlmClient(chatModel);
    }

    @Bean
    @ConditionalOnClass(EmbeddingModel.class)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean
    EmbeddingClient springAiEmbeddingClient(EmbeddingModel embeddingModel, TrustRagProperties properties) {
        return new SpringAiEmbeddingClient(
                embeddingModel,
                properties.getMilvus().getDimension(),
                embeddingModel.getClass().getSimpleName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "trust-rag.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    MilvusClientV2 milvusClient(TrustRagProperties properties) {
        TrustRagProperties.Milvus milvus = properties.getMilvus();
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(milvus.getUri())
                .dbName(milvus.getDatabase())
                .connectTimeoutMs(milvus.getConnectTimeoutMs())
                .rpcDeadlineMs(milvus.getRpcDeadlineMs());
        if (hasText(milvus.getToken())) {
            builder.token(milvus.getToken());
        } else if (hasText(milvus.getUsername())) {
            builder.username(milvus.getUsername()).password(milvus.getPassword());
        }
        return new MilvusClientV2(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "trust-rag.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeVectorStore.class)
    MilvusKnowledgeVectorStore milvusKnowledgeVectorStore(
            MilvusClientV2 client,
            TrustRagProperties properties) {
        TrustRagProperties.Milvus milvus = properties.getMilvus();
        MilvusKnowledgeVectorStore store = new MilvusKnowledgeVectorStore(
                client,
                new MilvusSettings(
                        milvus.getDatabase(),
                        milvus.getCollection(),
                        milvus.getDimension(),
                        milvus.getMetricType(),
                        milvus.isAutoCreateCollection()),
                new MilvusFilterBuilder());
        store.initialize();
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeVisibilityPolicy knowledgeVisibilityPolicy() {
        return new KnowledgeVisibilityPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeStateMachine knowledgeStateMachine() {
        return new KnowledgeStateMachine();
    }

    @Bean
    @ConditionalOnMissingBean
    QueryRewriteService queryRewriteService() {
        return new DefaultQueryRewriteService();
    }

    @Bean
    @ConditionalOnMissingBean
    RerankClient rerankClient() {
        return new NoOpRerankClient();
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeResolver scopeResolver() {
        return new DefaultScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    PrivacyFilter privacyFilter(TrustRagProperties properties) {
        TrustRagProperties.Privacy privacy = properties.getPrivacy();
        return new DefaultPrivacyFilter(
                privacy.isBlockApiKey(),
                privacy.isBlockDbUrl(),
                privacy.isBlockPhone());
    }

    @Bean
    @ConditionalOnMissingBean
    ScopeClassifier scopeClassifier() {
        return new DefaultScopeClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    CandidateExtractor candidateExtractor() {
        return new DefaultCandidateExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    ChunkStrategy chunkStrategy(TrustRagProperties properties) {
        return new RecursiveTextChunkStrategy(
                properties.getChunk().getSize(),
                properties.getChunk().getOverlap());
    }

    @Bean
    @ConditionalOnMissingBean
    PromptBuilder promptBuilder(ObjectProvider<PromptCustomizer> customizers) {
        return new DefaultPromptBuilder(customizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeGapDetector knowledgeGapDetector(GapDetectionOptions options) {
        return new RuleBasedKnowledgeGapDetector(options);
    }

    @Bean
    @ConditionalOnMissingBean
    TrustAwareRetriever trustAwareRetriever(
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            RetrievalOptions options) {
        return new TrustAwareRetriever(
                embeddingClient, vectorStore, knowledgeRepository, visibilityPolicy, options);
    }

    @Bean
    @ConditionalOnMissingBean
    CandidateKnowledgeService candidateKnowledgeService(
            KnowledgeRepository knowledgeRepository,
            PromotionTaskRepository promotionTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            LifecycleOptions lifecycleOptions,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new CandidateKnowledgeService(
                knowledgeRepository, promotionTaskRepository, lineageRepository,
                visibilityPolicy, lifecycleOptions, transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    TrustRagFeedbackService trustRagFeedbackService(
            FeedbackRepository feedbackRepository,
            RagTraceRepository traceRepository,
            KnowledgeRepository knowledgeRepository,
            PrivacyFilter privacyFilter,
            ScopeClassifier scopeClassifier,
            CandidateExtractor candidateExtractor,
            CandidateKnowledgeService candidateKnowledgeService,
            TransactionRunner transactionRunner) {
        return new DefaultTrustRagFeedbackService(
                feedbackRepository, traceRepository, knowledgeRepository,
                privacyFilter, scopeClassifier,
                candidateExtractor, candidateKnowledgeService, transactionRunner);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeIngestionService knowledgeIngestionService(
            ChunkStrategy chunkStrategy,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            ReviewTaskRepository reviewTaskRepository,
            TransactionRunner transactionRunner,
            LifecycleOptions lifecycleOptions,
            Clock clock) {
        return new KnowledgeIngestionService(
                chunkStrategy, embeddingClient, vectorStore, knowledgeRepository,
                visibilityPolicy, reviewTaskRepository, transactionRunner,
                lifecycleOptions, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeReviewService knowledgeReviewService(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            ObjectProvider<ReviewCallback> callbacks,
            TransactionRunner transactionRunner,
            KnowledgeStateMachine stateMachine,
            Clock clock) {
        List<ReviewCallback> callbackList = callbacks.orderedStream().toList();
        return new KnowledgeReviewService(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                embeddingClient, vectorStore, callbackList, transactionRunner,
                stateMachine, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    LlmPreReviewer llmPreReviewer(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            PromotionOptions options) {
        return options.llmPreReviewEnabled()
                ? new StructuredLlmPreReviewer(llmClient, objectMapper)
                : new NoOpLlmPreReviewer();
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeRelationJudge knowledgeRelationJudge(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            ConflictDetectionOptions options) {
        KnowledgeRelationJudge fallback = new RuleBasedKnowledgeRelationJudge();
        return options.llmJudgeEnabled()
                ? new StructuredKnowledgeRelationJudge(llmClient, objectMapper, fallback)
                : fallback;
    }

    @Bean
    @ConditionalOnMissingBean
    DuplicateDetector duplicateDetector(
            KnowledgeRepository knowledgeRepository,
            KnowledgeVectorStore vectorStore,
            DuplicateDetectionOptions options) {
        return new DefaultDuplicateDetector(knowledgeRepository, vectorStore, options);
    }

    @Bean
    @ConditionalOnMissingBean
    EvidenceVerifier evidenceVerifier(SourceScoreOptions sourceScoreOptions) {
        return new DefaultEvidenceVerifier(sourceScoreOptions);
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictDetector conflictDetector(
            KnowledgeRepository knowledgeRepository,
            KnowledgeVectorStore vectorStore,
            ConflictRecordRepository conflictRepository,
            KnowledgeRelationJudge relationJudge,
            ConflictDetectionOptions options,
            Clock clock) {
        return new DefaultConflictDetector(
                knowledgeRepository, vectorStore, conflictRepository,
                relationJudge, options, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgePromotionEngine knowledgePromotionEngine(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            PrivacyFilter privacyFilter,
            DuplicateDetector duplicateDetector,
            LlmPreReviewer preReviewer,
            EvidenceVerifier evidenceVerifier,
            ConflictDetector conflictDetector,
            PromotionOptions options,
            LifecycleOptions lifecycleOptions,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new DefaultKnowledgePromotionEngine(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                embeddingClient, vectorStore, privacyFilter, duplicateDetector,
                preReviewer, evidenceVerifier, conflictDetector, options,
                lifecycleOptions, stateMachine, transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    PromotionTaskService promotionTaskService(
            KnowledgeRepository knowledgeRepository,
            PromotionTaskRepository taskRepository,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new PromotionTaskService(
                knowledgeRepository, taskRepository, stateMachine, transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgePromotionWorker knowledgePromotionWorker(
            PromotionTaskRepository taskRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgePromotionEngine engine,
            PromotionOptions options,
            Clock clock) {
        return new KnowledgePromotionWorker(
                taskRepository, knowledgeRepository, engine, options.retryLimit(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeLifecycleManager knowledgeLifecycleManager(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeStateMachine stateMachine,
            LifecycleOptions options,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new DefaultKnowledgeLifecycleManager(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                embeddingClient, vectorStore, stateMachine, options,
                transactionRunner, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.promotion",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    TrustRagPromotionScheduler trustRagPromotionScheduler(
            PromotionTaskService taskService,
            KnowledgePromotionWorker worker,
            KnowledgeLifecycleManager lifecycleManager,
            PromotionOptions options) {
        return new TrustRagPromotionScheduler(
                taskService, worker, lifecycleManager, options.batchSize());
    }

    @Bean
    @ConditionalOnMissingBean
    TrustRagEngine trustRagEngine(
            QueryRewriteService queryRewriteService,
            TrustAwareRetriever retriever,
            RerankClient rerankClient,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            RagTraceRepository traceRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeGapDetector gapDetector,
            ScopeResolver scopeResolver,
            EngineOptions options,
            Clock clock) {
        return new DefaultTrustRagEngine(
                queryRewriteService, retriever, rerankClient, promptBuilder, llmClient,
                traceRepository, knowledgeRepository, gapDetector, scopeResolver, options, clock);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
