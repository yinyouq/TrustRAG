# TrustRAG Engine 模式工程化开发设计文档

> 项目定位：可插拔式可信自进化 RAG 框架  
> 交付形态：Java Jar / Spring Boot Starter  
> 默认向量数据库：Milvus  
> 推荐元数据数据库：PostgreSQL 或 MySQL  
> 核心接入方式：Engine 模式，即框架接管完整 RAG 子流程，业务 Agent 只调用统一入口  
> 目标：在不侵入业务 Agent 主流程的前提下，让项目具备 RAG 问答、知识缺口检测、低可信候选知识沉淀、人工审核入库和知识飞轮能力

---

## 1. Engine 模式的边界定义

### 1.1 Engine 模式是什么

Engine 模式指的是：

> 业务项目原来的 Agent 主流程可以保持不变，但凡涉及知识库问答、资料检索、RAG 生成的部分，都统一交给 TrustRAG Engine 执行。

业务代码只需要调用：

```java
RagAnswer answer = trustRagEngine.ask(request);
```

框架内部负责：

```text
Query Rewrite
  ↓
权限过滤
  ↓
Milvus 分层检索
  ↓
Hybrid Search 可选
  ↓
Rerank 可选
  ↓
Prompt 构造
  ↓
LLM 生成回答
  ↓
RagTrace 记录
  ↓
知识缺口检测
  ↓
候选知识抽取
  ↓
隐私过滤
  ↓
Scope 分类
  ↓
低可信池入库
  ↓
审核任务生成
```

### 1.2 Engine 模式解决的问题

普通 Agent 项目里，RAG 流程通常散落在业务代码中：

```text
Agent 规划
  ↓
业务代码自己检索知识库
  ↓
业务代码自己组装 Prompt
  ↓
业务代码自己调用 LLM
  ↓
业务代码自己处理回答
```

这样会带来几个问题：

1. 框架无法知道检索分数。
2. 框架无法知道哪些 Chunk 进入了 Prompt。
3. 框架无法知道回答是否依赖低可信知识。
4. 框架无法判断知识库是否真的缺失相关内容。
5. 框架无法自动记录完整问答轨迹。
6. 框架无法沉淀候选知识。

Engine 模式通过统一接管 RAG 子流程，让框架完整掌握一次 RAG 执行过程中的关键数据，从而实现知识缺口检测和后续知识飞轮逻辑。

### 1.3 Engine 模式的耦合边界

Engine 模式的耦合范围是：

```text
业务 Agent 主流程不变
RAG 子流程由框架接管
```

业务 Agent 仍然可以负责：

```text
任务规划
多工具调用
多轮对话编排
业务决策
结果汇总
权限认证
用户体系
项目体系
```

TrustRAG Engine 负责：

```text
知识检索
RAG 问答
知识缺口检测
候选知识沉淀
知识池管理
人工审核任务
知识生命周期
```

也就是说，框架不应该试图接管整个 Agent，而是专注做好“可信 RAG 子系统”。

---

## 2. 框架总体目标

### 2.1 第一版必须实现的能力

第一版应优先实现最小可用闭环：

```text
业务系统调用 TrustRagEngine.ask()
  ↓
框架完成 RAG 回答
  ↓
框架记录 RagTrace
  ↓
框架判断是否存在知识缺口
  ↓
如果存在缺口，生成低可信候选知识
  ↓
生成审核任务
  ↓
人工审核通过后进入高可信知识库
  ↓
下次类似问题可以召回新增知识
```

第一版必须包含：

1. Spring Boot Starter 自动装配。
2. Milvus 默认向量检索。
3. 关系型数据库存储知识元数据、日志、反馈和审核记录。
4. TrustRagEngine 统一入口。
5. 文档导入与知识入库。
6. 高可信池、低可信池。
7. RagTrace 全链路记录。
8. 规则型知识缺口检测。
9. 用户反馈接口。
10. 候选知识抽取。
11. 人工审核 API。
12. 审核通过后重新向量化并进入高可信池。

### 2.2 第一版暂不强制实现的能力

为了控制复杂度，第一版可以暂缓：

1. 中可信池。
2. LLM 批量预审。
3. 复杂冲突检测。
4. 复杂知识生命周期。
5. 多向量库适配。
6. 分布式任务调度。
7. 完整评估平台。
8. 自动爬取官方文档。
9. 全自动晋升。
10. GraphRAG。

这些能力可以在第二版、第三版扩展。

---

## 3. 推荐工程结构

### 3.1 Maven 多模块结构

建议使用多模块 Maven 项目：

```text
trust-rag
├── trust-rag-core
├── trust-rag-spring-boot-starter
├── trust-rag-milvus
├── trust-rag-storage-jdbc
├── trust-rag-admin-api
├── trust-rag-example-springboot
└── trust-rag-docs
```

### 3.2 模块职责

| 模块 | 职责 |
|---|---|
| trust-rag-core | 核心领域模型、接口、缺口检测、知识抽取、检索编排抽象 |
| trust-rag-spring-boot-starter | 自动装配、配置属性、默认 Bean 注册 |
| trust-rag-milvus | Milvus 向量检索实现 |
| trust-rag-storage-jdbc | JDBC / MyBatis / JPA 元数据存储实现 |
| trust-rag-admin-api | 候选知识、审核、反馈、日志查询等 HTTP API |
| trust-rag-example-springboot | 示例项目，演示如何接入 Jar |
| trust-rag-docs | 使用文档、配置说明、开发文档 |

### 3.3 依赖方向

依赖方向必须保持单向：

```text
starter
  ↓
core
  ↓
无具体框架依赖

milvus
  ↓
core

storage-jdbc
  ↓
core

admin-api
  ↓
core
```

不要让 `core` 依赖 Spring、Milvus SDK、具体大模型 SDK。  
`core` 只定义抽象、领域模型和默认算法。

---

## 4. 框架核心运行流程

### 4.1 ask 主流程

```text
TrustRagEngine.ask(request)
  ↓
创建 RagTrace
  ↓
校验请求参数
  ↓
填充默认 Scope
  ↓
Query Rewrite
  ↓
Trust-Aware Retrieval
  ↓
Rerank
  ↓
Prompt Build
  ↓
LLM Generate
  ↓
结果解析
  ↓
记录 QA Session
  ↓
记录 Retrieval Log
  ↓
Knowledge Gap Detection
  ↓
Candidate Knowledge Extraction
  ↓
Privacy Filter
  ↓
Scope Classifier
  ↓
保存低可信候选知识
  ↓
生成审核任务
  ↓
返回 RagAnswer
```

### 4.2 Engine 内部伪代码

```java
public class DefaultTrustRagEngine implements TrustRagEngine {

    private final QueryRewriteService queryRewriteService;
    private final TrustAwareRetriever trustAwareRetriever;
    private final RerankService rerankService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final RagTraceRecorder traceRecorder;
    private final KnowledgeGapDetectionPipeline gapDetectionPipeline;
    private final CandidateKnowledgePipeline candidateKnowledgePipeline;

    @Override
    public RagAnswer ask(RagRequest request) {
        RagTrace trace = RagTrace.start(request);

        try {
            validate(request);

            List<String> rewrittenQueries = queryRewriteService.rewrite(request);
            trace.setRewrittenQueries(rewrittenQueries);

            RetrievalResult retrievalResult = trustAwareRetriever.retrieve(request, rewrittenQueries);
            trace.setRetrievedChunks(retrievalResult.getChunks());

            List<RetrievedChunk> rerankedChunks = rerankService.rerank(request, retrievalResult.getChunks());
            trace.setRerankedChunks(rerankedChunks);

            List<RetrievedChunk> usedChunks = selectPromptChunks(rerankedChunks);
            trace.setUsedChunks(usedChunks);

            String prompt = promptBuilder.build(request, usedChunks);
            trace.setFinalPrompt(prompt);

            LlmResponse llmResponse = llmClient.generate(prompt);
            trace.setAnswer(llmResponse.getText());
            trace.setTokenUsage(llmResponse.getTokenUsage());

            GapDetectionResult gapResult = gapDetectionPipeline.detect(trace);
            trace.setGapDetectionResult(gapResult);

            if (gapResult.isShouldExtractCandidate()) {
                candidateKnowledgePipeline.process(trace, gapResult);
            }

            traceRecorder.record(trace);

            return RagAnswer.from(trace);

        } catch (Exception e) {
            trace.markFailed(e);
            traceRecorder.record(trace);
            throw e;
        }
    }
}
```

### 4.3 关键约束

1. 缺口检测不能影响正常回答返回。
2. 候选知识抽取失败不能导致问答失败。
3. Trace 记录失败不能导致问答失败，但必须打印错误日志。
4. LLM 调用失败需要明确抛出业务异常。
5. Milvus 检索失败需要明确返回检索异常，不要吞掉。
6. 用户隐私相关内容默认不保存完整 Prompt。

---

## 5. 对外暴露的核心接口

### 5.1 TrustRagEngine

这是业务项目最主要使用的接口。

```java
public interface TrustRagEngine {

    RagAnswer ask(RagRequest request);

    Flux<RagAnswerChunk> streamAsk(RagRequest request);
}
```

第一版可以先实现同步 `ask`，流式 `streamAsk` 可以第二阶段实现。

### 5.2 RagRequest

```java
public class RagRequest {

    private String question;

    private String userId;
    private String conversationId;
    private String projectId;
    private String tenantId;

    private Map<String, Object> metadata;

    private Boolean enableQueryRewrite;
    private Boolean enableRerank;
    private Boolean enableGapDetection;
    private Boolean enableCandidateExtraction;

    private Integer topK;
    private Double minScore;

    private String systemPrompt;
}
```

字段说明：

| 字段 | 是否必填 | 说明 |
|---|---|---|
| question | 是 | 用户问题 |
| userId | 否 | 用户 ID，不传则使用匿名用户 |
| conversationId | 否 | 会话 ID，用于会话级知识隔离 |
| projectId | 否 | 项目 ID |
| tenantId | 否 | 租户 ID |
| metadata | 否 | 业务扩展字段 |
| enableQueryRewrite | 否 | 是否启用 Query Rewrite |
| enableRerank | 否 | 是否启用 Rerank |
| enableGapDetection | 否 | 是否启用缺口检测 |
| enableCandidateExtraction | 否 | 是否启用候选知识抽取 |
| topK | 否 | 本次请求覆盖默认 TopK |
| minScore | 否 | 本次请求覆盖最低分数 |
| systemPrompt | 否 | 业务方可追加系统提示词 |

### 5.3 RagAnswer

```java
public class RagAnswer {

    private String traceId;

    private String answer;

    private Double confidence;

    private List<UsedKnowledge> usedKnowledge;

    private Boolean possibleGap;

    private GapDetectionResult gapDetectionResult;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long latencyMs;
}
```

### 5.4 UsedKnowledge

```java
public class UsedKnowledge {

    private Long knowledgeId;

    private String title;

    private String content;

    private String trustLevel;

    private String scopeType;

    private Double finalScore;

    private String sourceRef;
}
```

### 5.5 用户反馈接口

框架需要提供反馈入口。

```java
public interface TrustRagFeedbackService {

    void submitFeedback(RagFeedbackRequest request);
}
```

```java
public class RagFeedbackRequest {

    private String traceId;

    private String feedbackType;

    private String feedbackContent;

    private String correctedAnswer;

    private String userId;
}
```

反馈类型：

```text
like
dislike
correction
irrelevant_context
wrong_answer
missing_knowledge
```

---

## 6. 需要暴露给使用方自定义的扩展点

框架要能打成 Jar 给别人用，不能把所有逻辑写死。  
但也不能暴露太多接口，否则使用成本过高。

### 6.1 必须暴露的扩展点

| 扩展点 | 接口 | 目的 |
|---|---|---|
| LLM 调用 | LlmClient | 支持不同大模型 |
| Embedding | EmbeddingClient | 支持不同向量模型 |
| Rerank | RerankClient | 支持本地或云端重排 |
| Prompt 构造 | PromptCustomizer | 允许业务方追加 Prompt |
| 权限上下文 | ScopeResolver | 允许业务方定义 user/project/tenant |
| 文档解析 | DocumentParser | 支持不同文件类型 |
| Chunk 策略 | ChunkStrategy | 支持不同切分方式 |
| 缺口规则 | GapRule | 允许业务方增加缺口判断规则 |
| 候选知识抽取 | CandidateExtractor | 允许业务方自定义抽取逻辑 |
| 隐私过滤 | PrivacyFilter | 允许业务方增加敏感规则 |
| 审核回调 | ReviewCallback | 审核通过后通知业务系统 |

### 6.2 不建议第一版暴露的扩展点

第一版不建议暴露过细：

```text
Milvus 内部查询构造器
RagTrace 内部持久化细节
KnowledgePromotion 内部权重公式所有步骤
PromptBuilder 全量替换
Engine 主流程替换
```

否则框架边界会变乱。

建议采用：

```text
主流程固定
关键节点可插拔
配置参数可覆盖
```

### 6.3 使用 @ConditionalOnMissingBean 支持覆盖

使用方可以声明自己的 Bean 覆盖默认实现：

```java
@Bean
public KnowledgeGapDetector customKnowledgeGapDetector() {
    return new MyKnowledgeGapDetector();
}
```

框架自动装配时必须使用：

```java
@ConditionalOnMissingBean
```

---

## 7. 配置设计

### 7.1 application.yml 示例

```yaml
trust-rag:
  enabled: true

  engine:
    mode: engine
    enable-query-rewrite: true
    enable-rerank: true
    enable-gap-detection: true
    enable-candidate-extraction: true
    default-top-k: 8
    prompt-max-chunks: 5

  milvus:
    host: localhost
    port: 19530
    database: default
    collection: trust_rag_knowledge
    dimension: 1536
    metric-type: COSINE
    auto-create-collection: true

  retrieval:
    high-trust-top-k: 5
    medium-trust-top-k: 0
    low-trust-top-k: 3
    min-vector-score: 0.60
    min-rerank-score: 0.50

  trust-weight:
    high: 1.0
    medium: 0.7
    low-conversation: 0.6
    low-user: 0.45
    low-project: 0.45
    low-tenant: 0.35
    low-global-candidate: 0.15

  gap-detection:
    enabled: true
    no-retrieval-score: 0.90
    low-vector-score-threshold: 0.65
    low-rerank-score-threshold: 0.55
    gap-score-threshold: 0.50
    llm-judge-enabled: false
    uncertain-expressions:
      - "不确定"
      - "资料不足"
      - "无法判断"
      - "没有相关信息"

  candidate:
    enabled: true
    auto-create-review-task: true
    default-trust-level: low
    default-status: pending_review
    evidence-required: true

  privacy:
    enabled: true
    block-api-key: true
    block-db-url: true
    block-phone: true
    block-email: false

  review:
    enabled: true
    approve-to-trust-level: high

  trace:
    enabled: true
    save-prompt: false
    save-answer: true
    save-retrieval-log: true

  admin-api:
    enabled: true
```

### 7.2 配置原则

1. 默认配置应该能跑起来。
2. 安全相关配置默认偏保守。
3. 私有知识默认不跨用户。
4. Prompt 默认不全量保存，避免敏感信息落库。
5. 低可信知识默认不参与 global 召回。
6. 缺口检测默认使用规则模式，LLM 判断可选。
7. Milvus Collection 可以自动创建，但生产环境应允许关闭。
8. 中可信池第一版可以配置为 0，后续再启用。

---

## 8. 知识缺口检测设计

### 8.1 为什么必须基于 RagTrace

知识缺口检测不能只看模型回答。

必须同时看：

```text
用户问题
改写 Query
召回结果
向量分数
Rerank 分数
进入 Prompt 的 Chunk
LLM 回答
用户反馈
工具补充信息
```

所以 Engine 模式的核心对象是 `RagTrace`。

### 8.2 RagTrace 结构

```java
public class RagTrace {

    private String traceId;

    private String userId;
    private String conversationId;
    private String projectId;
    private String tenantId;

    private String originalQuestion;
    private List<String> rewrittenQueries;

    private List<RetrievedChunk> retrievedChunks;
    private List<RetrievedChunk> rerankedChunks;
    private List<RetrievedChunk> usedChunks;

    private String finalPrompt;
    private String answer;

    private Double maxVectorScore;
    private Double avgVectorScore;
    private Double maxRerankScore;
    private Double answerConfidence;

    private Boolean noContext;
    private Boolean onlyLowTrustMatched;
    private Boolean usedMediumTrustKnowledge;
    private Boolean usedLowTrustKnowledge;

    private GapDetectionResult gapDetectionResult;

    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;

    private String status;
    private String errorMessage;
}
```

### 8.3 缺口检测类型

```java
public enum KnowledgeGapType {

    NO_RETRIEVAL,
    LOW_RETRIEVAL_SCORE,
    LOW_RERANK_SCORE,
    INSUFFICIENT_CONTEXT,
    ONLY_LOW_TRUST_MATCHED,
    ANSWER_UNCERTAIN,
    USER_CORRECTION,
    NEGATIVE_FEEDBACK,
    TOOL_RESULT_MISSING,
    CONFLICT_FOUND,
    KNOWLEDGE_OUTDATED
}
```

### 8.4 缺口检测结果

```java
public class GapDetectionResult {

    private Boolean hasGap;

    private Double gapScore;

    private List<KnowledgeGapType> gapTypes;

    private String reason;

    private Boolean shouldExtractCandidate;

    private Boolean shouldCreateReviewTask;
}
```

### 8.5 规则型检测

第一版优先实现规则型检测。

```text
无召回 → 高概率缺口
最高向量分数过低 → 疑似缺口
Rerank 分数过低 → 疑似缺口
没有高可信知识，只命中低可信知识 → 正式知识库缺口
回答包含“不确定、资料不足”等表达 → 疑似缺口
用户纠错 → 强缺口信号
用户点踩 → 中等缺口信号
```

### 8.6 缺口检测伪代码

```java
public class RuleBasedKnowledgeGapDetector implements KnowledgeGapDetector {

    @Override
    public GapDetectionResult detect(RagTrace trace) {
        double score = 0.0;
        List<KnowledgeGapType> types = new ArrayList<>();

        if (trace.getRetrievedChunks().isEmpty()) {
            score += 0.90;
            types.add(KnowledgeGapType.NO_RETRIEVAL);
        }

        if (trace.getMaxVectorScore() != null && trace.getMaxVectorScore() < 0.65) {
            score += 0.30;
            types.add(KnowledgeGapType.LOW_RETRIEVAL_SCORE);
        }

        if (trace.getMaxRerankScore() != null && trace.getMaxRerankScore() < 0.55) {
            score += 0.30;
            types.add(KnowledgeGapType.LOW_RERANK_SCORE);
        }

        if (Boolean.TRUE.equals(trace.getOnlyLowTrustMatched())) {
            score += 0.35;
            types.add(KnowledgeGapType.ONLY_LOW_TRUST_MATCHED);
        }

        if (containsUncertainExpression(trace.getAnswer())) {
            score += 0.20;
            types.add(KnowledgeGapType.ANSWER_UNCERTAIN);
        }

        boolean hasGap = score >= 0.50;

        return GapDetectionResult.builder()
                .hasGap(hasGap)
                .gapScore(Math.min(score, 1.0))
                .gapTypes(types)
                .shouldExtractCandidate(hasGap)
                .shouldCreateReviewTask(hasGap)
                .build();
    }
}
```

### 8.7 注意：回答失败不等于知识缺口

框架必须避免把所有失败都当作缺口。

需要区分：

| 类型 | 说明 | 是否一定要补知识 |
|---|---|---|
| NO_KNOWLEDGE | 知识库确实没有 | 是 |
| RETRIEVAL_FAIL | 有知识但没召回 | 否，应优化检索 |
| RERANK_FAIL | 召回了但排序差 | 否，应优化重排 |
| GENERATION_FAIL | 召回了但模型没用好 | 否，应优化 Prompt |
| QUERY_AMBIGUOUS | 用户问题不清楚 | 否，应追问 |
| KNOWLEDGE_EXPIRED | 知识过期 | 是，但要更新旧知识 |

第一版可以先实现 `gap_reason` 字段，后续再扩展失败分类模型。

---

## 9. 候选知识抽取设计

### 9.1 候选知识从哪里来

候选知识来源：

```text
用户纠错内容
用户补充说明
工具查询结果
模型回答中的可验证结论
未命中的外部资料
人工手动添加
```

第一版建议只从两个来源抽取：

```text
用户纠错
人工手动添加
```

原因：

1. 用户纠错可信度相对更高。
2. 人工手动添加更可控。
3. 不建议第一版就让 LLM 从自己的回答里自动抽取知识，否则容易引入幻觉。

### 9.2 候选知识格式

```java
public class CandidateKnowledge {

    private String title;

    private String claim;

    private String content;

    private String evidence;

    private String sourceType;

    private String sourceRef;

    private String scopeType;

    private String trustLevel;

    private String status;

    private Double confidence;

    private List<String> tags;
}
```

### 9.3 抽取规则

错误做法：

```text
把整段模型回答直接入库。
```

正确做法：

```text
抽取一条清晰、短小、可验证的知识断言。
```

例如：

```json
{
  "title": "Spring AI ToolCallback 动态注册",
  "claim": "Spring AI 支持通过 ToolCallback 动态注册工具。",
  "content": "在 Spring AI 中，可以通过 ToolCallback 将外部工具能力注册给模型调用。",
  "sourceType": "user_correction",
  "scopeType": "global_candidate",
  "trustLevel": "low",
  "status": "pending_review"
}
```

---

## 10. Milvus 设计

### 10.1 存储原则

Milvus 只负责向量检索，不负责完整知识治理。

推荐：

```text
Milvus：存向量 + 检索必要字段
关系型数据库：存完整 metadata、审核、状态、版本、日志
```

### 10.2 Milvus Collection 字段

Collection：`trust_rag_knowledge`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Int64 | Milvus 主键 |
| knowledge_id | Int64 | 关系型数据库知识 ID |
| embedding | FloatVector | 向量 |
| trust_level | VarChar | high / medium / low |
| scope_type | VarChar | conversation / user / project / tenant / global |
| user_id | VarChar | 用户 ID |
| conversation_id | VarChar | 会话 ID |
| project_id | VarChar | 项目 ID |
| tenant_id | VarChar | 租户 ID |
| status | VarChar | enabled / pending_review / rejected / expired |
| created_at | Int64 | 创建时间戳 |

### 10.3 检索过滤条件

检索时必须先构造可见范围：

```text
global
tenant = 当前 tenantId
project = 当前 projectId
user = 当前 userId
conversation = 当前 conversationId
```

过滤条件示例：

```text
status == "enabled" &&
(
  scope_type == "global"
  OR (scope_type == "tenant" AND tenant_id == currentTenantId)
  OR (scope_type == "project" AND project_id == currentProjectId)
  OR (scope_type == "user" AND user_id == currentUserId)
  OR (scope_type == "conversation" AND conversation_id == currentConversationId)
)
```

### 10.4 为什么还需要关系型数据库

因为以下内容不适合只放 Milvus：

```text
审核任务
反馈记录
知识版本
拒绝原因
冲突记录
RagTrace
检索日志
用户权限
知识生命周期
```

Milvus 不是业务数据库。  
它只应该做向量检索。

---

## 11. 数据库表设计

### 11.1 knowledge_item

```sql
CREATE TABLE knowledge_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    title VARCHAR(255),
    claim TEXT,
    content TEXT NOT NULL,
    summary TEXT,

    knowledge_type VARCHAR(64),
    trust_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,

    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    project_id VARCHAR(64),
    tenant_id VARCHAR(64),

    source_type VARCHAR(64),
    source_ref TEXT,
    evidence TEXT,

    embedding_id VARCHAR(128),
    embedding_model VARCHAR(128),
    embedding_dimension INT,

    llm_score DECIMAL(5,4),
    source_score DECIMAL(5,4),
    feedback_score DECIMAL(5,4),
    conflict_score DECIMAL(5,4),
    privacy_score DECIMAL(5,4),
    promotion_score DECIMAL(5,4),

    usage_count INT DEFAULT 0,
    positive_feedback_count INT DEFAULT 0,
    negative_feedback_count INT DEFAULT 0,

    version INT DEFAULT 1,
    hash VARCHAR(128),

    approved_by VARCHAR(64),
    approved_at DATETIME,
    reject_reason TEXT,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME
);
```

说明：

1. 如果使用 MySQL，使用 `AUTO_INCREMENT`。
2. 如果使用 PostgreSQL，可以改为 `BIGSERIAL`。
3. embedding 不存关系库，只存 Milvus 对应 ID。
4. `hash` 用于去重。
5. `scope_type` 和 `trust_level` 是检索权限和排序权重核心字段。

### 11.2 rag_trace

```sql
CREATE TABLE rag_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    trace_id VARCHAR(64) NOT NULL UNIQUE,

    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    project_id VARCHAR(64),
    tenant_id VARCHAR(64),

    question TEXT NOT NULL,
    rewritten_query TEXT,
    answer TEXT,

    max_vector_score DECIMAL(8,6),
    avg_vector_score DECIMAL(8,6),
    max_rerank_score DECIMAL(8,6),
    answer_confidence DECIMAL(5,4),

    possible_gap BOOLEAN DEFAULT FALSE,
    gap_score DECIMAL(5,4),
    gap_types TEXT,
    gap_reason TEXT,

    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT,

    status VARCHAR(32),
    error_message TEXT,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 11.3 retrieval_log

```sql
CREATE TABLE retrieval_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    trace_id VARCHAR(64) NOT NULL,
    knowledge_id BIGINT,

    query TEXT,
    search_type VARCHAR(32),

    vector_score DECIMAL(8,6),
    keyword_score DECIMAL(8,6),
    rerank_score DECIMAL(8,6),
    trust_score DECIMAL(8,6),
    final_score DECIMAL(8,6),

    trust_level VARCHAR(32),
    scope_type VARCHAR(32),

    rank_no INT,
    used_in_prompt BOOLEAN DEFAULT FALSE,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 11.4 feedback

```sql
CREATE TABLE feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    trace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),

    feedback_type VARCHAR(64),
    feedback_content TEXT,
    corrected_answer TEXT,

    processed BOOLEAN DEFAULT FALSE,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 11.5 review_task

```sql
CREATE TABLE review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    knowledge_id BIGINT NOT NULL,

    status VARCHAR(32) NOT NULL,
    reviewer_id VARCHAR(64),
    review_action VARCHAR(64),
    review_comment TEXT,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME
);
```

---

## 12. Spring Boot Starter 自动装配

### 12.1 自动装配目标

使用方引入依赖后，只需要配置：

```yaml
trust-rag:
  enabled: true
```

就能自动注册：

```text
TrustRagEngine
MilvusKnowledgeVectorStore
KnowledgeRepository
RagTraceRecorder
KnowledgeGapDetector
CandidateKnowledgeService
ReviewService
```

### 12.2 自动装配类

```java
@Configuration
@EnableConfigurationProperties(TrustRagProperties.class)
@ConditionalOnProperty(prefix = "trust-rag", name = "enabled", havingValue = "true")
public class TrustRagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TrustRagEngine trustRagEngine(...) {
        return new DefaultTrustRagEngine(...);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeGapDetector knowledgeGapDetector(TrustRagProperties properties) {
        return new RuleBasedKnowledgeGapDetector(properties.getGapDetection());
    }
}
```

### 12.3 自动装配检查

1. 缺少 Milvus 配置时，启动阶段给出明确错误。
2. 缺少 LlmClient 时，启动阶段给出明确错误。
3. 缺少 EmbeddingClient 时，知识入库功能不可用，但可允许只使用检索。
4. Admin API 通过配置开关决定是否注册 Controller。
5. 所有默认 Bean 使用 `@ConditionalOnMissingBean`。

---

## 13. 使用方接入方式

### 13.1 引入依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>trust-rag-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 13.2 配置

```yaml
trust-rag:
  enabled: true
  milvus:
    host: localhost
    port: 19530
    collection: trust_rag_knowledge
    dimension: 1536
  gap-detection:
    enabled: true
```

### 13.3 调用

```java
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final TrustRagEngine trustRagEngine;

    @PostMapping("/chat")
    public RagAnswer chat(@RequestBody ChatRequest request) {
        return trustRagEngine.ask(
                RagRequest.builder()
                        .question(request.getQuestion())
                        .userId(request.getUserId())
                        .conversationId(request.getConversationId())
                        .projectId(request.getProjectId())
                        .tenantId(request.getTenantId())
                        .build()
        );
    }
}
```

---

## 14. 管理 API 设计

第一版可以提供内置 Controller，也可以只提供 Service。  
建议通过配置开关控制是否开启内置 API。

```yaml
trust-rag:
  admin-api:
    enabled: true
```

### 14.1 候选知识列表

```http
GET /trust-rag/admin/knowledge/candidates
```

参数：

```text
status=pending_review
trustLevel=low
scopeType=global_candidate
```

### 14.2 审核通过

```http
POST /trust-rag/admin/knowledge/{id}/approve
```

请求：

```json
{
  "reviewerId": "admin",
  "comment": "确认内容正确"
}
```

### 14.3 审核拒绝

```http
POST /trust-rag/admin/knowledge/{id}/reject
```

请求：

```json
{
  "reviewerId": "admin",
  "reason": "内容无来源证据"
}
```

### 14.4 提交反馈

```http
POST /trust-rag/feedback
```

请求：

```json
{
  "traceId": "trace_001",
  "feedbackType": "correction",
  "feedbackContent": "这里说错了，正确说法是...",
  "correctedAnswer": "..."
}
```

---

## 15. 开发流程

### 阶段 1：搭建多模块工程

目标：建立 Jar 框架骨架。

任务：

1. 创建 Maven 父工程。
2. 创建 `trust-rag-core`。
3. 创建 `trust-rag-spring-boot-starter`。
4. 创建 `trust-rag-milvus`。
5. 创建 `trust-rag-storage-jdbc`。
6. 创建 `trust-rag-example-springboot`。
7. 统一 Java 版本，建议 Java 17。
8. 统一依赖管理。

交付物：

```text
多模块工程可以编译
example 项目可以引用 starter
```

检查点：

1. core 不依赖 Spring。
2. starter 只负责装配。
3. milvus 实现只依赖 core 接口。
4. example 不写框架内部逻辑。

---

### 阶段 2：定义核心领域模型和接口

目标：先稳定框架抽象。

任务：

1. 定义 `TrustRagEngine`。
2. 定义 `RagRequest`。
3. 定义 `RagAnswer`。
4. 定义 `RagTrace`。
5. 定义 `RetrievedChunk`。
6. 定义 `KnowledgeItem`。
7. 定义 `KnowledgeGapDetector`。
8. 定义 `GapDetectionResult`。
9. 定义 `CandidateKnowledge`。
10. 定义 `LlmClient`。
11. 定义 `EmbeddingClient`。
12. 定义 `KnowledgeVectorStore`。

交付物：

```text
核心接口和模型稳定
```

检查点：

1. Engine 不依赖具体 Milvus SDK。
2. Engine 不依赖具体大模型厂商。
3. Engine 不依赖具体 ORM。
4. 所有实现都通过接口注入。

---

### 阶段 3：实现 Milvus 向量存储

目标：完成默认向量数据库能力。

任务：

1. 接入 Milvus Java SDK。
2. 实现 Collection 自动创建。
3. 实现向量写入。
4. 实现向量删除。
5. 实现向量检索。
6. 支持 metadata filter。
7. 支持按 trust_level 过滤。
8. 支持按 scope_type 过滤。
9. 支持 Collection 初始化检查。
10. 支持 dimension 配置校验。

交付物：

```text
MilvusKnowledgeVectorStore 可用
```

检查点：

1. Collection 不存在时可自动创建。
2. dimension 不一致时明确报错。
3. Milvus 连接失败时错误信息清晰。
4. 检索时必须包含权限过滤条件。
5. 不允许未过滤直接全库检索私有知识。

---

### 阶段 4：实现元数据存储

目标：完成关系型数据库持久化。

任务：

1. 建表 `knowledge_item`。
2. 建表 `rag_trace`。
3. 建表 `retrieval_log`。
4. 建表 `feedback`。
5. 建表 `review_task`。
6. 实现 Repository。
7. 支持 MySQL 或 PostgreSQL。
8. 支持 Flyway 或 Liquibase 初始化脚本。
9. 实现知识状态更新。
10. 实现审核任务生成。

交付物：

```text
知识、Trace、反馈、审核任务可落库
```

检查点：

1. 关系库和 Milvus 数据需要通过 `knowledge_id` 关联。
2. 审核通过时要同时更新关系库和 Milvus 状态。
3. 审核拒绝时 Milvus 中对应向量应该删除或标记不可检索。
4. 失败时要避免关系库成功、Milvus 失败造成脏数据。
5. 需要补偿任务处理写入失败。

---

### 阶段 5：实现文档导入和高可信知识入库

目标：支持基础知识库建设。

任务：

1. 实现手动添加知识接口。
2. 实现文本导入。
3. 实现 Markdown 导入。
4. 实现基础 Chunk 切分。
5. 调用 EmbeddingClient。
6. 写入 `knowledge_item`。
7. 写入 Milvus。
8. 默认 `trust_level = high` 或由用户指定。
9. 默认 `scope_type = global` 或由用户指定。

交付物：

```text
可以导入知识并通过 Milvus 检索
```

检查点：

1. 每条知识必须有 scope_type。
2. 每条知识必须有 trust_level。
3. 每条知识必须有 status。
4. 不允许无 scope 的知识进入 Milvus。
5. 重复内容要通过 hash 初步去重。

---

### 阶段 6：实现 TrustAwareRetriever

目标：实现 Engine 模式下的可信检索。

任务：

1. 根据 RagRequest 构造可见 Scope。
2. 分别检索高可信知识。
3. 检索当前会话低可信知识。
4. 检索当前用户低可信知识。
5. 检索当前项目低可信知识。
6. 合并检索结果。
7. 去重。
8. 计算 trust_score。
9. 计算 final_score。
10. 返回 RetrievalResult。

交付物：

```text
按可信度和作用域检索可用
```

检查点：

1. 先权限过滤，再检索。
2. 不允许低可信私有知识被其他用户召回。
3. 高可信知识权重高于低可信知识。
4. 低可信知识不能覆盖高可信知识。
5. 检索日志完整记录。

---

### 阶段 7：实现 PromptBuilder 和 LLM 调用

目标：完成完整 RAG 回答。

任务：

1. 实现默认 Prompt 模板。
2. Prompt 中区分 high / medium / low。
3. Prompt 中声明冲突时优先 high。
4. 支持业务方追加 systemPrompt。
5. 调用 LlmClient。
6. 返回 Answer。
7. 记录 token 和耗时。

交付物：

```text
TrustRagEngine.ask() 可以返回答案
```

检查点：

1. Prompt 不应无脑塞入过多 Chunk。
2. 低可信知识必须标记。
3. 没有高可信知识时，回答要更谨慎。
4. LLM 调用失败要有明确异常。
5. 可以配置是否保存 Prompt，默认不保存完整 Prompt。

---

### 阶段 8：实现 RagTrace 全链路记录

目标：为缺口检测提供数据基础。

任务：

1. ask 开始时生成 traceId。
2. 记录原始问题。
3. 记录改写 Query。
4. 记录召回 Chunk。
5. 记录分数。
6. 记录进入 Prompt 的 Chunk。
7. 记录回答。
8. 记录耗时和 token。
9. 记录异常。
10. 保存到 `rag_trace` 和 `retrieval_log`。

交付物：

```text
每次 RAG 调用都可追踪
```

检查点：

1. 即使调用失败也要记录 trace。
2. 敏感 Prompt 默认不全量保存。
3. retrieval_log 要能还原当时召回情况。
4. traceId 必须返回给业务方，便于后续反馈。

---

### 阶段 9：实现规则型知识缺口检测

目标：实现第一版缺口检测。

任务：

1. 从 RagTrace 提取 GapSignals。
2. 实现无召回检测。
3. 实现低向量分数检测。
4. 实现低 Rerank 分数检测。
5. 实现 onlyLowTrustMatched 检测。
6. 实现回答不确定表达检测。
7. 计算 gapScore。
8. 生成 GapDetectionResult。
9. 将结果写入 rag_trace。
10. 返回给 RagAnswer。

交付物：

```text
RAG 回答后可以判断 possibleGap
```

检查点：

1. 阈值必须可配置。
2. 缺口检测不能抛异常影响正常回答。
3. 不要把所有失败都当知识缺口。
4. 用户纠错应作为强信号，但通过 feedback 流程处理。
5. 第一版不建议默认开启 LLM 缺口判断。

---

### 阶段 10：实现反馈和候选知识抽取

目标：让用户纠错进入低可信池。

任务：

1. 实现反馈接口。
2. 支持 like、dislike、correction。
3. correction 触发候选知识抽取。
4. 从 correctedAnswer 中抽取 claim。
5. 生成 CandidateKnowledge。
6. 执行 PrivacyFilter。
7. 执行 ScopeClassifier。
8. 写入 `knowledge_item`，trust_level = low。
9. status = pending_review。
10. 生成 review_task。

交付物：

```text
用户纠错 → 低可信候选知识 → 审核任务
```

检查点：

1. 不要直接把用户纠错写入高可信池。
2. 不要直接把 LLM 原回答抽取为知识。
3. 候选知识必须带 source_type。
4. 候选知识必须带 evidence。
5. 缺少证据时也可以保存，但不得自动晋升。

---

### 阶段 11：实现人工审核

目标：完成最小知识飞轮闭环。

任务：

1. 查询候选知识列表。
2. 查询候选知识详情。
3. 审核通过。
4. 审核拒绝。
5. 修改后通过。
6. 通过后更新 trust_level = high。
7. 更新 status = enabled。
8. 重新生成 embedding。
9. 写入 Milvus 或更新 Milvus metadata。
10. 拒绝时更新 status = rejected。

交付物：

```text
低可信候选知识可以人工审核进入高可信池
```

检查点：

1. 审核通过必须记录 reviewer。
2. 审核拒绝必须记录 reject_reason。
3. 修改后通过要增加 version。
4. Milvus 写入失败时不能只更新数据库。
5. 审核操作要有事务和补偿机制。

---

### 阶段 12：完善 Starter 可用性

目标：让别人能真正以 Jar 形式接入。

任务：

1. 编写自动配置类。
2. 编写配置属性类。
3. 编写默认 Bean。
4. 支持用户自定义 Bean 覆盖。
5. 编写 example 项目。
6. 编写 README。
7. 编写 application.yml 示例。
8. 编写数据库初始化脚本。
9. 编写 Milvus Docker Compose。
10. 编写最小运行 Demo。

交付物：

```text
其他 Spring Boot 项目引入依赖后可运行
```

检查点：

1. 缺少必要配置时启动报错清晰。
2. 可选能力缺少依赖时不应影响基础功能。
3. 默认配置安全。
4. 示例项目能从零启动。
5. Jar 包没有强制侵入业务 Controller。

---

## 16. 第一版功能清单

### 16.1 必须包含

```text
Spring Boot Starter
TrustRagEngine.ask()
Milvus 默认检索
关系型数据库元数据
知识导入
基础 Chunk
Embedding 接口
LLM 接口
Prompt 构造
RagTrace
RetrievalLog
规则型知识缺口检测
Feedback API
用户纠错生成候选知识
低可信池
人工审核 API
审核通过进入高可信池
```

### 16.2 可以延后

```text
中可信池
LLM 预审
复杂冲突检测
流式回答
Hybrid Search
BGE Rerank
知识生命周期自动降权
多向量库适配
管理后台页面
GraphRAG
Agent 工具调用观察
```

### 16.3 可选增强

```text
RerankClient
QueryRewriteService
PromptCustomizer
PrivacyFilter 自定义规则
ScopeResolver
ReviewCallback
MetricCollector
```

---

## 17. 最小演示场景

### 17.1 初始状态

知识库没有：

```text
Spring AI ToolCallback 支持动态注册工具
```

用户提问：

```text
Spring AI 能不能动态注册工具？
```

系统检索不到高可信知识，回答不确定，并标记 possibleGap = true。

### 17.2 用户纠错

用户提交反馈：

```text
你说错了，Spring AI 可以通过 ToolCallback 动态注册工具。
```

系统生成低可信候选知识：

```text
claim = Spring AI 可以通过 ToolCallback 动态注册工具。
trust_level = low
status = pending_review
source_type = user_correction
```

### 17.3 人工审核

管理员审核通过。

系统更新：

```text
trust_level = high
status = enabled
写入 Milvus
```

### 17.4 再次提问

用户再次提问：

```text
Spring AI 能不能动态注册工具？
```

系统召回新增高可信知识，并给出明确回答。

这就是第一版最小知识飞轮闭环。

---

## 18. 工程风险检查

### 18.1 数据一致性风险

问题：

```text
关系库写入成功，但 Milvus 写入失败。
```

处理：

```text
1. knowledge_item 状态先设为 indexing。
2. Milvus 写入成功后改为 enabled。
3. Milvus 写入失败则设为 index_failed。
4. 定时补偿任务重试 index_failed。
```

### 18.2 隐私泄露风险

问题：

```text
低可信私有知识被其他用户召回。
```

处理：

```text
1. 检索前必须构造 scope filter。
2. Milvus 查询必须带 filter。
3. 关系库二次校验。
4. 默认低可信知识不进入 global。
```

### 18.3 知识污染风险

问题：

```text
错误候选知识进入高可信池。
```

处理：

```text
1. 用户纠错只能进入 low。
2. LLM 输出不能直接进入 high。
3. high 必须人工审核。
4. 审核记录可追踪。
```

### 18.4 缺口误判风险

问题：

```text
检索失败被误判为知识缺失。
```

处理：

```text
1. 缺口检测只标记 possibleGap。
2. 不自动入高可信池。
3. 后续通过审核确认。
4. 记录 gapTypes 方便复盘。
```

### 18.5 Starter 侵入性风险

问题：

```text
Jar 引入后强制注册太多 Controller 或 Bean。
```

处理：

```text
1. 所有能力通过 enabled 控制。
2. Controller 默认可关闭。
3. 核心 Bean 使用 ConditionalOnMissingBean。
4. 默认只暴露 TrustRagEngine。
```

### 18.6 性能风险

问题：

```text
每次 ask 都执行过多后处理，导致响应慢。
```

处理：

```text
1. 缺口检测规则同步执行。
2. 候选知识抽取可以异步。
3. 审核任务生成可以异步。
4. Trace 记录失败不阻塞主流程。
5. Rerank 默认可关闭。
```

---

## 19. 最终设计校验

### 19.1 是否满足低耦合

满足。

业务方只需要：

```java
trustRagEngine.ask(request);
```

Agent 主流程不需要重写。

### 19.2 是否能判断知识缺口

能。

因为 Engine 模式内部掌握：

```text
问题
检索结果
向量分数
Prompt 上下文
模型回答
用户反馈
```

这些是缺口检测所需的核心信号。

### 19.3 是否避免知识污染

基本能。

因为第一版只允许：

```text
低可信候选池 → 人工审核 → 高可信池
```

没有自动进入正式库。

### 19.4 是否适合打成 Jar

适合。

因为通过 Spring Boot Starter 可以做到：

```text
自动装配默认实现
配置参数控制行为
接口允许用户替换实现
业务项目按需调用 Engine
```

### 19.5 是否需要 Milvus 之外的数据库

需要。

Milvus 只做向量检索，不适合存储完整审核、状态、日志、反馈、版本。  
必须配合 MySQL 或 PostgreSQL。

### 19.6 第一版是否过重

控制后不过重。

第一版只做：

```text
Engine
Milvus
Metadata DB
Trace
规则缺口检测
低可信池
人工审核
```

不做：

```text
中可信池
LLM 预审
复杂冲突检测
复杂生命周期
```

---

## 20. 最终结论

Engine 模式第一版的正确开发目标不是做完整自进化 RAG，而是先跑通最小闭环：

```text
RAG 问答
  ↓
RagTrace 记录
  ↓
规则型知识缺口检测
  ↓
用户纠错生成候选知识
  ↓
低可信池
  ↓
人工审核
  ↓
高可信池
  ↓
再次召回成功
```

这个闭环跑通后，框架才真正具备“越用越好”的基础能力。

第一版框架应该包含：

```text
TrustRagEngine
Milvus 检索
元数据存储
RagTrace
规则缺口检测
低可信池
反馈接口
人工审核接口
高可信池入库
Spring Boot Starter 自动装配
```

第一版框架应该暴露给用户修改：

```text
LlmClient
EmbeddingClient
RerankClient
PromptCustomizer
ScopeResolver
GapRule
PrivacyFilter
ChunkStrategy
ReviewCallback
配置参数
```

第一版框架不应该暴露：

```text
Engine 主流程完全替换
Milvus 内部查询细节
Trace 持久化内部细节
所有权重公式内部步骤
审核状态机随意改写
```

核心原则是：

```text
主流程稳定
关键节点可插拔
安全策略默认保守
知识晋升必须可审计
私有知识默认不跨域
```

按照这个设计实现，TrustRAG Engine 可以作为一个可插拔的 Jar 包接入其他 Spring Boot 项目，同时保持业务 Agent 主流程低侵入，并具备工程上可落地的知识缺口检测和知识飞轮能力。
