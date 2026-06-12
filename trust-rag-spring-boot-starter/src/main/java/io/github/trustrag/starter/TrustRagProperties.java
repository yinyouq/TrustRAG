package io.github.trustrag.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
        private int lowTrustTopK = 3;
        private double minVectorScore = 0.60;

        public int getHighTrustTopK() {
            return highTrustTopK;
        }

        public void setHighTrustTopK(int highTrustTopK) {
            this.highTrustTopK = highTrustTopK;
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
    }

    public static class TrustWeight {
        private double high = 1.0;
        private double lowConversation = 0.60;
        private double lowUser = 0.45;
        private double lowProject = 0.45;
        private double lowTenant = 0.35;

        public double getHigh() {
            return high;
        }

        public void setHigh(double high) {
            this.high = high;
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
}
