package io.github.trustrag.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RagRequest 表示一次领域请求，承载调用方传入的业务参数。
 */
public final class RagRequest {

    private final String question;
    private final String userId;
    private final String conversationId;
    private final String projectId;
    private final String tenantId;
    private final Map<String, Object> metadata;
    private final Boolean enableQueryRewrite;
    private final Boolean enableRerank;
    private final Boolean enableGapDetection;
    private final Boolean enableCandidateExtraction;
    private final Boolean evaluationMode;
    private final Long evalRunId;
    private final Long evalCaseId;
    private final Integer topK;
    private final Double minScore;
    private final String systemPrompt;

    private RagRequest(Builder builder) {
        this.question = builder.question;
        this.userId = builder.userId;
        this.conversationId = builder.conversationId;
        this.projectId = builder.projectId;
        this.tenantId = builder.tenantId;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.enableQueryRewrite = builder.enableQueryRewrite;
        this.enableRerank = builder.enableRerank;
        this.enableGapDetection = builder.enableGapDetection;
        this.enableCandidateExtraction = builder.enableCandidateExtraction;
        this.evaluationMode = builder.evaluationMode;
        this.evalRunId = builder.evalRunId;
        this.evalCaseId = builder.evalCaseId;
        this.topK = builder.topK;
        this.minScore = builder.minScore;
        this.systemPrompt = builder.systemPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String question() {
        return question;
    }

    public String userId() {
        return userId;
    }

    public String conversationId() {
        return conversationId;
    }

    public String projectId() {
        return projectId;
    }

    public String tenantId() {
        return tenantId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Boolean enableQueryRewrite() {
        return enableQueryRewrite;
    }

    public Boolean enableRerank() {
        return enableRerank;
    }

    public Boolean enableGapDetection() {
        return enableGapDetection;
    }

    public Boolean enableCandidateExtraction() {
        return enableCandidateExtraction;
    }

    public Boolean evaluationMode() {
        return evaluationMode;
    }

    public boolean isEvaluationMode() {
        return Boolean.TRUE.equals(evaluationMode);
    }

    public Long evalRunId() {
        return evalRunId;
    }

    public Long evalCaseId() {
        return evalCaseId;
    }

    public Integer topK() {
        return topK;
    }

    public Double minScore() {
        return minScore;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public static final class Builder {
        private String question;
        private String userId;
        private String conversationId;
        private String projectId;
        private String tenantId;
        private Map<String, Object> metadata = Map.of();
        private Boolean enableQueryRewrite;
        private Boolean enableRerank;
        private Boolean enableGapDetection;
        private Boolean enableCandidateExtraction;
        private Boolean evaluationMode;
        private Long evalRunId;
        private Long evalCaseId;
        private Integer topK;
        private Double minScore;
        private String systemPrompt;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? Map.of() : metadata;
            return this;
        }

        public Builder enableQueryRewrite(Boolean value) {
            this.enableQueryRewrite = value;
            return this;
        }

        public Builder enableRerank(Boolean value) {
            this.enableRerank = value;
            return this;
        }

        public Builder enableGapDetection(Boolean value) {
            this.enableGapDetection = value;
            return this;
        }

        public Builder enableCandidateExtraction(Boolean value) {
            this.enableCandidateExtraction = value;
            return this;
        }

        public Builder evaluationMode(Boolean value) {
            this.evaluationMode = value;
            return this;
        }

        public Builder evalRunId(Long evalRunId) {
            this.evalRunId = evalRunId;
            return this;
        }

        public Builder evalCaseId(Long evalCaseId) {
            this.evalCaseId = evalCaseId;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public RagRequest build() {
            return new RagRequest(this);
        }
    }
}
