package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.config.EngineOptions;
import io.github.trustrag.core.config.ConflictDetectionOptions;
import io.github.trustrag.core.config.DuplicateDetectionOptions;
import io.github.trustrag.core.config.GapDetectionOptions;
import io.github.trustrag.core.config.HybridRetrievalOptions;
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
import io.github.trustrag.core.service.DefaultKnowledgeIndexService;
import io.github.trustrag.core.service.DefaultKnowledgePromotionEngine;
import io.github.trustrag.core.service.DefaultPrivacyFilter;
import io.github.trustrag.core.service.DefaultPromptBuilder;
import io.github.trustrag.core.service.DefaultQueryRewriteService;
import io.github.trustrag.core.service.DefaultRrfFusionService;
import io.github.trustrag.core.service.DefaultScopeClassifier;
import io.github.trustrag.core.service.DefaultScopeResolver;
import io.github.trustrag.core.service.DefaultTrustRagEngine;
import io.github.trustrag.core.service.DefaultTrustRagFeedbackService;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import io.github.trustrag.core.service.KnowledgePromotionWorker;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.service.KnowledgeStateMachine;
import io.github.trustrag.core.service.KnowledgeVisibilityPolicy;
import io.github.trustrag.core.service.IndexSyncWorker;
import io.github.trustrag.core.service.NoOpRerankClient;
import io.github.trustrag.core.service.NoOpLlmPreReviewer;
import io.github.trustrag.core.service.NoOpKnowledgeKeywordStore;
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
import io.github.trustrag.core.spi.IndexSyncTaskRepository;
import io.github.trustrag.core.spi.KnowledgeGapDetector;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import io.github.trustrag.core.spi.KnowledgeLineageRepository;
import io.github.trustrag.core.spi.KnowledgeIndexService;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
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
import io.github.trustrag.core.spi.RrfFusionService;
import io.github.trustrag.core.spi.ReviewCallback;
import io.github.trustrag.core.spi.ReviewTaskRepository;
import io.github.trustrag.core.spi.ScopeClassifier;
import io.github.trustrag.core.spi.ScopeResolver;
import io.github.trustrag.core.spi.TransactionRunner;
import io.github.trustrag.document.CompositeDocumentParser;
import io.github.trustrag.document.DocumentImportService;
import io.github.trustrag.document.DocumentImportSettings;
import io.github.trustrag.document.DocumentImportTaskRepository;
import io.github.trustrag.document.DocumentImportWorker;
import io.github.trustrag.document.DocumentParser;
import io.github.trustrag.document.DocumentSourceLoader;
import io.github.trustrag.document.GitDocumentSourceLoader;
import io.github.trustrag.document.GitSourcePolicy;
import io.github.trustrag.document.HtmlDocumentParser;
import io.github.trustrag.document.LocalDocumentStorage;
import io.github.trustrag.document.MarkdownDocumentParser;
import io.github.trustrag.document.PdfDocumentParser;
import io.github.trustrag.document.TikaDocumentParser;
import io.github.trustrag.document.UploadDocumentSourceLoader;
import io.github.trustrag.document.WikiDocumentParser;
import io.github.trustrag.document.WordDocumentParser;
import io.github.trustrag.evaluation.DefaultEvalRunner;
import io.github.trustrag.evaluation.DefaultGenerationJudgeService;
import io.github.trustrag.evaluation.EvalCaseService;
import io.github.trustrag.evaluation.EvalDatasetService;
import io.github.trustrag.evaluation.EvalReportService;
import io.github.trustrag.evaluation.EvalRunService;
import io.github.trustrag.evaluation.EvalRunner;
import io.github.trustrag.evaluation.EvalTraceReader;
import io.github.trustrag.evaluation.EvaluationOptions;
import io.github.trustrag.evaluation.EvaluationRepository;
import io.github.trustrag.evaluation.GenerationJudgeService;
import io.github.trustrag.evaluation.GovernanceMetricService;
import io.github.trustrag.evaluation.NoOpGenerationJudgeService;
import io.github.trustrag.evaluation.RetrievalMetricCalculator;
import io.github.trustrag.milvus.MilvusFilterBuilder;
import io.github.trustrag.milvus.MilvusKnowledgeVectorStore;
import io.github.trustrag.milvus.MilvusSettings;
import io.github.trustrag.opensearch.OpenSearchKnowledgeKeywordStore;
import io.github.trustrag.opensearch.OpenSearchSettings;
import io.github.trustrag.storage.jdbc.JdbcFeedbackRepository;
import io.github.trustrag.storage.jdbc.JdbcConflictRecordRepository;
import io.github.trustrag.storage.jdbc.JdbcKnowledgeLineageRepository;
import io.github.trustrag.storage.jdbc.JdbcKnowledgeRepository;
import io.github.trustrag.storage.jdbc.JdbcIndexSyncTaskRepository;
import io.github.trustrag.storage.jdbc.JdbcPromotionTaskRepository;
import io.github.trustrag.storage.jdbc.JdbcRagTraceRepository;
import io.github.trustrag.storage.jdbc.JdbcReviewTaskRepository;
import io.github.trustrag.storage.jdbc.JdbcDocumentImportTaskRepository;
import io.github.trustrag.storage.jdbc.JdbcEvaluationRepository;
import io.github.trustrag.storage.jdbc.JdbcEvalTraceReader;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.trustrag.core.model.RetrievalMode;

/**
 * TrustRAG Spring Boot 自动装配入口。
 *
 * <p>该配置类把核心 SPI 默认绑定到 JDBC、Milvus、OpenSearch 和 Spring AI。
 * 业务方可以通过自定义同类型 Bean 覆盖默认实现。</p>
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(TrustRagProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "trust-rag", name = "enabled", havingValue = "true")
public class TrustRagAutoConfiguration {

    private static final System.Logger LOGGER =
            System.getLogger(TrustRagAutoConfiguration.class.getName());

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
                properties.getRetrieval().getPromptTopK(),
                properties.getTrace().isSavePrompt());
    }

    @Bean
    @ConditionalOnMissingBean
    RetrievalOptions trustRagRetrievalOptions(TrustRagProperties properties) {
        TrustRagProperties.Retrieval retrieval = properties.getRetrieval();
        TrustRagProperties.TrustWeight weights = properties.getTrustWeight();
        return new RetrievalOptions(
                properties.getEngine().getDefaultTopK(),
                retrieval.getHighTrustTopK(),
                retrieval.getMediumTrustTopK(),
                retrieval.getLowConversationTopK(),
                retrieval.getLowUserTopK(),
                retrieval.getLowProjectTopK(),
                retrieval.getLowTenantTopK(),
                retrieval.getLowGlobalCandidateTopK(),
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
    HybridRetrievalOptions trustRagHybridRetrievalOptions(
            TrustRagProperties properties) {
        TrustRagProperties.Retrieval retrieval = properties.getRetrieval();
        RetrievalMode mode = RetrievalMode.valueOf(
                retrieval.getMode()
                        .trim()
                        .replace('-', '_')
                        .toUpperCase(Locale.ROOT));
        if (!properties.getRrf().isEnabled()) {
            mode = RetrievalMode.VECTOR;
        }
        return new HybridRetrievalOptions(
                mode,
                retrieval.getFusionTopN(),
                properties.getMilvus().getVectorTopK(),
                properties.getOpensearch().getKeywordTopK(),
                properties.getRrf().getK(),
                properties.getTrustBoost().getHigh(),
                properties.getTrustBoost().getMedium(),
                properties.getTrustBoost().getLow());
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
        // 默认持久化走关系库，便于治理状态和索引补偿任务保持事务一致。
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
    IndexSyncTaskRepository indexSyncTaskRepository(DataSource dataSource) {
        return new JdbcIndexSyncTaskRepository(
                new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    DocumentImportTaskRepository documentImportTaskRepository(DataSource dataSource) {
        return new JdbcDocumentImportTaskRepository(
                new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvaluationOptions trustRagEvaluationOptions(TrustRagProperties properties) {
        TrustRagProperties.Evaluation evaluation = properties.getEvaluation();
        TrustRagProperties.Runner runner = evaluation.getRunner();
        TrustRagProperties.Metrics metrics = evaluation.getMetrics();
        TrustRagProperties.Judge judge = evaluation.getJudge();
        TrustRagProperties.Governance governance = evaluation.getGovernance();
        return new EvaluationOptions(
                evaluation.isEnabled(),
                new EvaluationOptions.Runner(
                        runner.getThreadPoolSize(),
                        runner.getCaseTimeoutSeconds(),
                        runner.isSaveEvalTrace()),
                new EvaluationOptions.Metrics(
                        metrics.getRecallKValues(),
                        metrics.getPrecisionKValues(),
                        metrics.getNdcgKValues()),
                new EvaluationOptions.Judge(
                        judge.isEnabled(),
                        judge.getModel(),
                        judge.getTemperature(),
                        judge.getMaxRetry(),
                        judge.isSavePrompt(),
                        judge.isSaveOutput()),
                new EvaluationOptions.Governance(
                        governance.isSnapshotEnabled(),
                        governance.getSnapshotCron()));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvaluationRepository evaluationRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcEvaluationRepository(
                new NamedParameterJdbcTemplate(dataSource), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalTraceReader evalTraceReader(DataSource dataSource) {
        return new JdbcEvalTraceReader(new NamedParameterJdbcTemplate(dataSource));
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
        try {
            store.initialize();
        } catch (RuntimeException exception) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Milvus initialization failed; retrieval will use keyword fallback until recovery",
                    exception);
        }
        return store;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "trust-rag.opensearch",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeKeywordStore.class)
    OpenSearchKnowledgeKeywordStore openSearchKnowledgeKeywordStore(
            TrustRagProperties properties) {
        TrustRagProperties.OpenSearch openSearch = properties.getOpensearch();
        OpenSearchKnowledgeKeywordStore store =
                OpenSearchKnowledgeKeywordStore.connect(new OpenSearchSettings(
                        openSearch.getUris(),
                        openSearch.getUsername(),
                        openSearch.getPassword(),
                        openSearch.getIndexName(),
                        openSearch.isAutoCreateIndex()));
        try {
            store.initialize();
        } catch (RuntimeException exception) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "OpenSearch initialization failed; retrieval will use vector fallback until recovery",
                    exception);
        }
        return store;
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeKeywordStore.class)
    KnowledgeKeywordStore noOpKnowledgeKeywordStore() {
        return new NoOpKnowledgeKeywordStore();
    }

    @Bean
    @ConditionalOnMissingBean
    RrfFusionService rrfFusionService() {
        return new DefaultRrfFusionService();
    }

    @Bean(name = "trustRagRetrievalExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "trustRagRetrievalExecutor")
    ExecutorService trustRagRetrievalExecutor(TrustRagProperties properties) {
        int parallelism = Math.max(2, properties.getRetrieval().getParallelism());
        return Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "trust-rag-retrieval");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeIndexService knowledgeIndexService(
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore,
            IndexSyncTaskRepository taskRepository,
            Clock clock) {
        return new DefaultKnowledgeIndexService(
                vectorStore, keywordStore, taskRepository, clock);
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
            KnowledgeKeywordStore keywordStore,
            KnowledgeRepository knowledgeRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            RetrievalOptions options,
            HybridRetrievalOptions hybridOptions,
            RrfFusionService fusionService,
            @Qualifier("trustRagRetrievalExecutor") Executor executor) {
        return new TrustAwareRetriever(
                embeddingClient, vectorStore, keywordStore, knowledgeRepository,
                visibilityPolicy, options, hybridOptions, fusionService, executor);
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
            KnowledgeIndexService indexService,
            KnowledgeRepository knowledgeRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgeVisibilityPolicy visibilityPolicy,
            ReviewTaskRepository reviewTaskRepository,
            TransactionRunner transactionRunner,
            LifecycleOptions lifecycleOptions,
            Clock clock) {
        return new KnowledgeIngestionService(
                chunkStrategy, embeddingClient, indexService, knowledgeRepository,
                lineageRepository, visibilityPolicy, reviewTaskRepository, transactionRunner,
                lifecycleOptions, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    DocumentImportSettings documentImportSettings(TrustRagProperties properties) {
        TrustRagProperties.DocumentImport value = properties.getDocument();
        return new DocumentImportSettings(
                Path.of(value.getStoragePath()),
                value.getMaxUploadBytes(),
                value.getMaxGitFileBytes(),
                value.getMaxGitFiles(),
                value.getBatchSize(),
                value.getRetryLimit(),
                value.isKeepSourceFiles(),
                value.isGitRemoteEnabled(),
                DocumentImportSettings.normalize(value.getGitAllowedHosts()),
                DocumentImportSettings.normalizePaths(value.getGitAllowedLocalRoots()),
                DocumentImportSettings.normalize(value.getAllowedExtensions()));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    LocalDocumentStorage localDocumentStorage(DocumentImportSettings settings) {
        return new LocalDocumentStorage(settings);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    GitSourcePolicy gitSourcePolicy(DocumentImportSettings settings) {
        return new GitSourcePolicy(settings);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    DocumentParser documentParser() {
        return new CompositeDocumentParser(List.of(
                new PdfDocumentParser(),
                new WordDocumentParser(),
                new HtmlDocumentParser(),
                new MarkdownDocumentParser(),
                new WikiDocumentParser(),
                new TikaDocumentParser()));
    }

    @Bean(name = "trustRagDocumentSourceLoaders")
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "trustRagDocumentSourceLoaders")
    List<DocumentSourceLoader> trustRagDocumentSourceLoaders(
            LocalDocumentStorage storage,
            DocumentImportSettings settings,
            GitSourcePolicy gitSourcePolicy) {
        return List.of(
                new UploadDocumentSourceLoader(storage),
                new GitDocumentSourceLoader(settings, gitSourcePolicy));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    DocumentImportService documentImportService(
            DocumentImportTaskRepository taskRepository,
            LocalDocumentStorage storage,
            DocumentImportSettings settings,
            GitSourcePolicy gitSourcePolicy,
            Clock clock) {
        return new DocumentImportService(
                taskRepository, storage, settings, gitSourcePolicy, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    DocumentImportWorker documentImportWorker(
            DocumentImportTaskRepository taskRepository,
            KnowledgeIngestionService ingestionService,
            DocumentParser parser,
            @Qualifier("trustRagDocumentSourceLoaders") List<DocumentSourceLoader> sourceLoaders,
            DocumentImportSettings settings,
            LocalDocumentStorage storage,
            Clock clock) {
        return new DocumentImportWorker(
                taskRepository, ingestionService, parser, sourceLoaders, settings, storage, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.document",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    TrustRagDocumentImportScheduler trustRagDocumentImportScheduler(
            DocumentImportWorker worker) {
        return new TrustRagDocumentImportScheduler(worker);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    RetrievalMetricCalculator retrievalMetricCalculator() {
        return new RetrievalMetricCalculator();
    }

    @Bean(name = "trustRagEvaluationExecutor")
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "trustRagEvaluationExecutor")
    ThreadPoolTaskExecutor trustRagEvaluationExecutor(TrustRagProperties properties) {
        int poolSize = Math.max(1, properties.getEvaluation().getRunner().getThreadPoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(poolSize * 100);
        executor.setThreadNamePrefix("trust-rag-eval-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    GenerationJudgeService generationJudgeService(
            ObjectProvider<LlmClient> llmClient,
            ObjectMapper objectMapper,
            EvaluationOptions options,
            Clock clock) {
        LlmClient client = llmClient.getIfAvailable();
        return options.judge().enabled() && client != null
                ? new DefaultGenerationJudgeService(client, objectMapper, options.judge(), clock)
                : new NoOpGenerationJudgeService();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalReportService evalReportService(
            EvaluationRepository repository,
            ObjectMapper objectMapper,
            Clock clock) {
        return new EvalReportService(repository, objectMapper, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalRunner evalRunner(
            EvaluationRepository repository,
            EvalTraceReader traceReader,
            TrustRagEngine engine,
            RetrievalMetricCalculator metricCalculator,
            GenerationJudgeService judgeService,
            EvalReportService reportService,
            Clock clock) {
        return new DefaultEvalRunner(
                repository, traceReader, engine, metricCalculator,
                judgeService, reportService, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalRunService evalRunService(
            EvaluationRepository repository,
            EvalRunner runner,
            @Qualifier("trustRagEvaluationExecutor") Executor executor,
            Clock clock) {
        return new EvalRunService(repository, runner, executor, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalDatasetService evalDatasetService(EvaluationRepository repository, Clock clock) {
        return new EvalDatasetService(repository, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    EvalCaseService evalCaseService(EvaluationRepository repository, Clock clock) {
        return new EvalCaseService(repository, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    GovernanceMetricService governanceMetricService(EvaluationRepository repository, Clock clock) {
        return new GovernanceMetricService(repository, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.evaluation.governance",
            name = "snapshot-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    TrustRagEvaluationScheduler trustRagEvaluationScheduler(
            GovernanceMetricService governanceMetricService) {
        return new TrustRagEvaluationScheduler(governanceMetricService);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeReviewService knowledgeReviewService(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            ObjectProvider<ReviewCallback> callbacks,
            TransactionRunner transactionRunner,
            KnowledgeStateMachine stateMachine,
            Clock clock) {
        List<ReviewCallback> callbackList = callbacks.orderedStream().toList();
        return new KnowledgeReviewService(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                embeddingClient, indexService, callbackList, transactionRunner,
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
            KnowledgeIndexService indexService,
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
                embeddingClient, indexService, privacyFilter, duplicateDetector,
                preReviewer, evidenceVerifier, conflictDetector, options,
                lifecycleOptions, stateMachine, transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    PromotionTaskService promotionTaskService(
            KnowledgeRepository knowledgeRepository,
            PromotionTaskRepository taskRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgeStateMachine stateMachine,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new PromotionTaskService(
                knowledgeRepository, taskRepository, lineageRepository,
                stateMachine, transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgePromotionWorker knowledgePromotionWorker(
            PromotionTaskRepository taskRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeLineageRepository lineageRepository,
            KnowledgePromotionEngine engine,
            PromotionOptions options,
            Clock clock) {
        return new KnowledgePromotionWorker(
                taskRepository, knowledgeRepository, lineageRepository,
                engine, options.retryLimit(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeLifecycleManager knowledgeLifecycleManager(
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            PromotionTaskRepository promotionTaskRepository,
            EmbeddingClient embeddingClient,
            KnowledgeIndexService indexService,
            KnowledgeStateMachine stateMachine,
            LifecycleOptions options,
            TransactionRunner transactionRunner,
            Clock clock) {
        return new DefaultKnowledgeLifecycleManager(
                knowledgeRepository, reviewTaskRepository, lineageRepository,
                promotionTaskRepository, embeddingClient, indexService, stateMachine, options,
                transactionRunner, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IndexSyncWorker indexSyncWorker(
            IndexSyncTaskRepository taskRepository,
            KnowledgeRepository knowledgeRepository,
            ReviewTaskRepository reviewTaskRepository,
            KnowledgeLineageRepository lineageRepository,
            EmbeddingClient embeddingClient,
            KnowledgeVectorStore vectorStore,
            KnowledgeKeywordStore keywordStore,
            TransactionRunner transactionRunner,
            TrustRagProperties properties,
            Clock clock) {
        return new IndexSyncWorker(
                taskRepository, knowledgeRepository, reviewTaskRepository,
                lineageRepository, embeddingClient, vectorStore, keywordStore,
                transactionRunner, properties.getIndexSync().getRetryLimit(), clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trust-rag.index-sync",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    TrustRagIndexSyncScheduler trustRagIndexSyncScheduler(
            IndexSyncWorker worker,
            TrustRagProperties properties) {
        return new TrustRagIndexSyncScheduler(
                worker, properties.getIndexSync().getBatchSize());
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
