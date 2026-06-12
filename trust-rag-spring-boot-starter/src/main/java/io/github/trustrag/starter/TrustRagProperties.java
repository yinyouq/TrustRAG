package io.github.trustrag.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("trust-rag")
public class TrustRagProperties {

    private boolean enabled;
    private final Engine engine = new Engine();
    private final Milvus milvus = new Milvus();
    private final Retrieval retrieval = new Retrieval();
    private final TrustWeight trustWeight = new TrustWeight();
    private final GapDetection gapDetection = new GapDetection();
    private final Privacy privacy = new Privacy();
    private final Chunk chunk = new Chunk();
    private final Trace trace = new Trace();
    private final AdminApi adminApi = new AdminApi();
    private final Promotion promotion = new Promotion();
    private final DuplicateDetection duplicateDetection = new DuplicateDetection();
    private final ConflictDetection conflictDetection = new ConflictDetection();
    private final SourceScore sourceScore = new SourceScore();
    private final Lifecycle lifecycle = new Lifecycle();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Engine getEngine() {
        return engine;
    }

    public Milvus getMilvus() {
        return milvus;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public TrustWeight getTrustWeight() {
        return trustWeight;
    }

    public GapDetection getGapDetection() {
        return gapDetection;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Trace getTrace() {
        return trace;
    }

    public AdminApi getAdminApi() {
        return adminApi;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public DuplicateDetection getDuplicateDetection() {
        return duplicateDetection;
    }

    public ConflictDetection getConflictDetection() {
        return conflictDetection;
    }

    public SourceScore getSourceScore() {
        return sourceScore;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public static class Engine {
        private boolean enableQueryRewrite = true;
        private boolean enableRerank;
        private boolean enableGapDetection = true;
        private boolean enableCandidateExtraction = true;
        private int defaultTopK = 8;
        private int promptMaxChunks = 5;

        public boolean isEnableQueryRewrite() {
            return enableQueryRewrite;
        }

        public void setEnableQueryRewrite(boolean enableQueryRewrite) {
            this.enableQueryRewrite = enableQueryRewrite;
        }

        public boolean isEnableRerank() {
            return enableRerank;
        }

        public void setEnableRerank(boolean enableRerank) {
            this.enableRerank = enableRerank;
        }

        public boolean isEnableGapDetection() {
            return enableGapDetection;
        }

        public void setEnableGapDetection(boolean enableGapDetection) {
            this.enableGapDetection = enableGapDetection;
        }

        public boolean isEnableCandidateExtraction() {
            return enableCandidateExtraction;
        }

        public void setEnableCandidateExtraction(boolean enableCandidateExtraction) {
            this.enableCandidateExtraction = enableCandidateExtraction;
        }

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public int getPromptMaxChunks() {
            return promptMaxChunks;
        }

        public void setPromptMaxChunks(int promptMaxChunks) {
            this.promptMaxChunks = promptMaxChunks;
        }
    }

    public static class Milvus {
        private boolean enabled = true;
        private String uri = "http://localhost:19530";
        private String token;
        private String username;
        private String password;
        private String database = "default";
        private String collection = "trust_rag_knowledge";
        private int dimension = 1536;
        private String metricType = "COSINE";
        private boolean autoCreateCollection = true;
        private long connectTimeoutMs = 10000;
        private long rpcDeadlineMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public String getMetricType() {
            return metricType;
        }

        public void setMetricType(String metricType) {
            this.metricType = metricType;
        }

        public boolean isAutoCreateCollection() {
            return autoCreateCollection;
        }

        public void setAutoCreateCollection(boolean autoCreateCollection) {
            this.autoCreateCollection = autoCreateCollection;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getRpcDeadlineMs() {
            return rpcDeadlineMs;
        }

        public void setRpcDeadlineMs(long rpcDeadlineMs) {
            this.rpcDeadlineMs = rpcDeadlineMs;
        }
    }

    public static class Retrieval {
        private int highTrustTopK = 5;
        private int mediumTrustTopK = 3;
        private int lowTrustTopK = 3;
        private double minVectorScore = 0.60;
        private boolean allowGlobalLowCandidate;

        public int getHighTrustTopK() {
            return highTrustTopK;
        }

        public void setHighTrustTopK(int highTrustTopK) {
            this.highTrustTopK = highTrustTopK;
        }

        public int getMediumTrustTopK() {
            return mediumTrustTopK;
        }

        public void setMediumTrustTopK(int mediumTrustTopK) {
            this.mediumTrustTopK = mediumTrustTopK;
        }

        public int getLowTrustTopK() {
            return lowTrustTopK;
        }

        public void setLowTrustTopK(int lowTrustTopK) {
            this.lowTrustTopK = lowTrustTopK;
        }

        public double getMinVectorScore() {
            return minVectorScore;
        }

        public void setMinVectorScore(double minVectorScore) {
            this.minVectorScore = minVectorScore;
        }

        public boolean isAllowGlobalLowCandidate() {
            return allowGlobalLowCandidate;
        }

        public void setAllowGlobalLowCandidate(boolean allowGlobalLowCandidate) {
            this.allowGlobalLowCandidate = allowGlobalLowCandidate;
        }
    }

    public static class TrustWeight {
        private double high = 1.0;
        private double medium = 0.70;
        private double lowConversation = 0.60;
        private double lowUser = 0.45;
        private double lowProject = 0.45;
        private double lowTenant = 0.35;
        private double lowGlobalCandidate = 0.20;

        public double getHigh() {
            return high;
        }

        public void setHigh(double high) {
            this.high = high;
        }

        public double getMedium() {
            return medium;
        }

        public void setMedium(double medium) {
            this.medium = medium;
        }

        public double getLowConversation() {
            return lowConversation;
        }

        public void setLowConversation(double lowConversation) {
            this.lowConversation = lowConversation;
        }

        public double getLowUser() {
            return lowUser;
        }

        public void setLowUser(double lowUser) {
            this.lowUser = lowUser;
        }

        public double getLowProject() {
            return lowProject;
        }

        public void setLowProject(double lowProject) {
            this.lowProject = lowProject;
        }

        public double getLowTenant() {
            return lowTenant;
        }

        public void setLowTenant(double lowTenant) {
            this.lowTenant = lowTenant;
        }

        public double getLowGlobalCandidate() {
            return lowGlobalCandidate;
        }

        public void setLowGlobalCandidate(double lowGlobalCandidate) {
            this.lowGlobalCandidate = lowGlobalCandidate;
        }
    }

    public static class GapDetection {
        private double lowVectorScoreThreshold = 0.65;
        private double lowRerankScoreThreshold = 0.55;
        private double gapScoreThreshold = 0.50;
        private List<String> uncertainExpressions = new ArrayList<>(
                List.of("不确定", "资料不足", "无法判断", "没有相关信息"));

        public double getLowVectorScoreThreshold() {
            return lowVectorScoreThreshold;
        }

        public void setLowVectorScoreThreshold(double lowVectorScoreThreshold) {
            this.lowVectorScoreThreshold = lowVectorScoreThreshold;
        }

        public double getLowRerankScoreThreshold() {
            return lowRerankScoreThreshold;
        }

        public void setLowRerankScoreThreshold(double lowRerankScoreThreshold) {
            this.lowRerankScoreThreshold = lowRerankScoreThreshold;
        }

        public double getGapScoreThreshold() {
            return gapScoreThreshold;
        }

        public void setGapScoreThreshold(double gapScoreThreshold) {
            this.gapScoreThreshold = gapScoreThreshold;
        }

        public List<String> getUncertainExpressions() {
            return uncertainExpressions;
        }

        public void setUncertainExpressions(List<String> uncertainExpressions) {
            this.uncertainExpressions = uncertainExpressions;
        }
    }

    public static class Privacy {
        private boolean blockApiKey = true;
        private boolean blockDbUrl = true;
        private boolean blockPhone = true;

        public boolean isBlockApiKey() {
            return blockApiKey;
        }

        public void setBlockApiKey(boolean blockApiKey) {
            this.blockApiKey = blockApiKey;
        }

        public boolean isBlockDbUrl() {
            return blockDbUrl;
        }

        public void setBlockDbUrl(boolean blockDbUrl) {
            this.blockDbUrl = blockDbUrl;
        }

        public boolean isBlockPhone() {
            return blockPhone;
        }

        public void setBlockPhone(boolean blockPhone) {
            this.blockPhone = blockPhone;
        }
    }

    public static class Chunk {
        private int size = 700;
        private int overlap = 100;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    public static class Trace {
        private boolean savePrompt;

        public boolean isSavePrompt() {
            return savePrompt;
        }

        public void setSavePrompt(boolean savePrompt) {
            this.savePrompt = savePrompt;
        }
    }

    public static class AdminApi {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Promotion {
        private boolean enabled = true;
        private int batchSize = 50;
        private int retryLimit = 3;
        private double minPromotionScore = 0.75;
        private double minSourceScore = 0.60;
        private double minEvidenceScore = 0.50;
        private double maxConflictRisk = 0.30;
        private double maxPrivacyRisk = 0.30;
        private boolean llmPreReviewEnabled = true;
        private String schedule = "0 0 3 * * ?";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getRetryLimit() { return retryLimit; }
        public void setRetryLimit(int retryLimit) { this.retryLimit = retryLimit; }
        public double getMinPromotionScore() { return minPromotionScore; }
        public void setMinPromotionScore(double value) { this.minPromotionScore = value; }
        public double getMinSourceScore() { return minSourceScore; }
        public void setMinSourceScore(double value) { this.minSourceScore = value; }
        public double getMinEvidenceScore() { return minEvidenceScore; }
        public void setMinEvidenceScore(double value) { this.minEvidenceScore = value; }
        public double getMaxConflictRisk() { return maxConflictRisk; }
        public void setMaxConflictRisk(double value) { this.maxConflictRisk = value; }
        public double getMaxPrivacyRisk() { return maxPrivacyRisk; }
        public void setMaxPrivacyRisk(double value) { this.maxPrivacyRisk = value; }
        public boolean isLlmPreReviewEnabled() { return llmPreReviewEnabled; }
        public void setLlmPreReviewEnabled(boolean value) { this.llmPreReviewEnabled = value; }
        public String getSchedule() { return schedule; }
        public void setSchedule(String schedule) { this.schedule = schedule; }
    }

    public static class DuplicateDetection {
        private boolean enabled = true;
        private boolean hashEnabled = true;
        private boolean vectorEnabled = true;
        private double similarityThreshold = 0.92;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isHashEnabled() { return hashEnabled; }
        public void setHashEnabled(boolean value) { this.hashEnabled = value; }
        public boolean isVectorEnabled() { return vectorEnabled; }
        public void setVectorEnabled(boolean value) { this.vectorEnabled = value; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double value) { this.similarityThreshold = value; }
    }

    public static class ConflictDetection {
        private boolean enabled = true;
        private boolean compareWithHigh = true;
        private boolean compareWithMedium = true;
        private int topK = 5;
        private double similarityThreshold = 0.75;
        private boolean llmJudgeEnabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isCompareWithHigh() { return compareWithHigh; }
        public void setCompareWithHigh(boolean value) { this.compareWithHigh = value; }
        public boolean isCompareWithMedium() { return compareWithMedium; }
        public void setCompareWithMedium(boolean value) { this.compareWithMedium = value; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double value) { this.similarityThreshold = value; }
        public boolean isLlmJudgeEnabled() { return llmJudgeEnabled; }
        public void setLlmJudgeEnabled(boolean value) { this.llmJudgeEnabled = value; }
    }

    public static class SourceScore {
        private Map<String, Double> scores = defaults();

        public Map<String, Double> getScores() { return scores; }
        public void setScores(Map<String, Double> scores) { this.scores = scores; }

        private static Map<String, Double> defaults() {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("official", 0.95);
            values.put("manual", 0.85);
            values.put("document", 0.80);
            values.put("database", 0.80);
            values.put("user_correction", 0.60);
            values.put("conversation", 0.45);
            values.put("unknown", 0.10);
            return values;
        }
    }

    public static class Lifecycle {
        private int lowTtlDays = 30;
        private int mediumTtlDays = 180;
        private boolean autoExpireEnabled = true;
        private int negativeFeedbackDowngradeThreshold = 3;
        private int indexFailedRetryLimit = 3;

        public int getLowTtlDays() { return lowTtlDays; }
        public void setLowTtlDays(int value) { this.lowTtlDays = value; }
        public int getMediumTtlDays() { return mediumTtlDays; }
        public void setMediumTtlDays(int value) { this.mediumTtlDays = value; }
        public boolean isAutoExpireEnabled() { return autoExpireEnabled; }
        public void setAutoExpireEnabled(boolean value) { this.autoExpireEnabled = value; }
        public int getNegativeFeedbackDowngradeThreshold() {
            return negativeFeedbackDowngradeThreshold;
        }
        public void setNegativeFeedbackDowngradeThreshold(int value) {
            this.negativeFeedbackDowngradeThreshold = value;
        }
        public int getIndexFailedRetryLimit() { return indexFailedRetryLimit; }
        public void setIndexFailedRetryLimit(int value) { this.indexFailedRetryLimit = value; }
    }
}
