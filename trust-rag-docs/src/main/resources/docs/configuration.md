# 配置说明

## 最小配置

```yaml
trust-rag:
  enabled: true
  admin-api:
    enabled: false
  milvus:
    uri: http://localhost:19530
    collection: trust_rag_knowledge
    dimension: 1536
```

宿主应用还需要提供 `DataSource`、Spring AI `ChatModel` 和 `EmbeddingModel`。

## 完整示例

```yaml
trust-rag:
  enabled: true
  engine:
    enable-query-rewrite: true
    enable-rerank: false
    enable-gap-detection: true
    enable-candidate-extraction: true
    default-top-k: 8
    prompt-max-chunks: 5
  milvus:
    enabled: true
    uri: http://localhost:19530
    database: default
    collection: trust_rag_knowledge
    dimension: 1536
    metric-type: COSINE
    auto-create-collection: true
    connect-timeout-ms: 10000
    rpc-deadline-ms: 30000
  retrieval:
    high-trust-top-k: 5
    medium-trust-top-k: 3
    low-trust-top-k: 3
    min-vector-score: 0.60
    allow-global-low-candidate: false
  trust-weight:
    high: 1.0
    medium: 0.70
    low-conversation: 0.60
    low-user: 0.45
    low-project: 0.45
    low-tenant: 0.35
    low-global-candidate: 0.20
  promotion:
    enabled: true
    batch-size: 50
    retry-limit: 3
    min-promotion-score: 0.75
    min-source-score: 0.60
    min-evidence-score: 0.50
    max-conflict-risk: 0.30
    max-privacy-risk: 0.30
    llm-pre-review-enabled: true
    schedule: "0 0 3 * * ?"
  duplicate-detection:
    enabled: true
    hash-enabled: true
    vector-enabled: true
    similarity-threshold: 0.92
  conflict-detection:
    enabled: true
    compare-with-high: true
    compare-with-medium: true
    top-k: 5
    similarity-threshold: 0.75
    llm-judge-enabled: true
  source-score:
    scores:
      official: 0.95
      manual: 0.85
      document: 0.80
      database: 0.80
      user_correction: 0.60
      conversation: 0.45
      unknown: 0.10
  lifecycle:
    low-ttl-days: 30
    medium-ttl-days: 180
    auto-expire-enabled: true
    negative-feedback-downgrade-threshold: 3
    # 每轮信任级别最多重试的 INDEX_FAILED 数量；0 表示关闭自动重试
    index-failed-retry-limit: 3
  gap-detection:
    low-vector-score-threshold: 0.65
    low-rerank-score-threshold: 0.55
    gap-score-threshold: 0.50
    uncertain-expressions:
      - 不确定
      - 资料不足
      - 无法判断
      - 没有相关信息
  privacy:
    block-api-key: true
    block-db-url: true
    block-phone: true
  chunk:
    size: 700
    overlap: 100
  trace:
    save-prompt: false
  admin-api:
    enabled: false
```

## 数据库迁移

PostgreSQL：

```yaml
spring:
  flyway:
    locations: classpath:db/migration
```

MySQL：

```yaml
spring:
  flyway:
    locations: classpath:db/mysql
```

V2 会新增治理字段以及 `promotion_task`、`conflict_record`、`knowledge_lineage`，并把旧版 `ENABLED/PENDING_REVIEW` 状态迁移到三池状态。

## 自定义扩展

声明同类型 Bean 即可覆盖默认实现，包括：

- `LlmClient`、`EmbeddingClient`、`KnowledgeVectorStore`
- `QueryRewriteService`、`RerankClient`
- `PromptBuilder` 或多个 `PromptCustomizer`
- `PrivacyFilter`、`ScopeResolver`、`ScopeClassifier`
- `CandidateExtractor`、`DuplicateDetector`、`ConflictDetector`
- `LlmPreReviewer`、`EvidenceVerifier`、`KnowledgeRelationJudge`
- `ReviewCallback`、`TransactionRunner`

生产环境关闭 `auto-create-collection` 前，应预建与配置维度一致的 Milvus Collection。Embedding 模型发生变化时，应新建 Collection 或执行全量重建索引。
