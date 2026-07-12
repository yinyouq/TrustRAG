# 配置说明

## 最小配置

```yaml
trust-rag:
  enabled: true
  admin-api:
    enabled: false
  milvus:
    uri: http://localhost:19530
    collection: trust_rag_knowledge_vector
    dimension: 1536
  opensearch:
    uris:
      - http://localhost:9200
    index-name: trust_rag_knowledge_keyword
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
    collection: trust_rag_knowledge_vector
    dimension: 1536
    metric-type: COSINE
    vector-top-k: 30
    auto-create-collection: true
    connect-timeout-ms: 10000
    rpc-deadline-ms: 30000
  opensearch:
    enabled: true
    uris:
      - http://localhost:9200
    username:
    password:
    index-name: trust_rag_knowledge_keyword
    keyword-top-k: 30
    auto-create-index: true
  retrieval:
    mode: hybrid-rrf
    prompt-top-k: 5
    fusion-top-n: 30
    parallelism: 2
    high-trust-top-k: 5
    medium-trust-top-k: 3
    low-conversation-top-k: 2
    low-user-top-k: 2
    low-project-top-k: 2
    low-tenant-top-k: 2
    low-global-candidate-top-k: 1
    min-vector-score: 0.60
    allow-global-low-candidate: false
  rrf:
    enabled: true
    k: 60
  trust-boost:
    high: 1.0
    medium: 0.92
    low: 0.80
  trust-weight:
    high: 1.0
    medium: 0.70
    low-conversation: 0.60
    low-user: 0.45
    low-project: 0.45
    low-tenant: 0.35
    low-global-candidate: 0.15
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
      official_doc: 0.95
      internal_doc: 0.90
      database_result: 0.85
      tool_result: 0.80
      user_correction: 0.60
      llm_summary: 0.30
      unknown: 0.10
  lifecycle:
    low-ttl-days: 30
    medium-ttl-days: 90
    auto-expire-enabled: true
    negative-feedback-downgrade-threshold: 3
    # 每个 INDEX_RETRY 任务的最大失败次数；0 表示关闭自动重试
    index-failed-retry-limit: 3
  index-sync:
    enabled: true
    batch-size: 100
    retry-limit: 3
    fixed-delay-ms: 30000
  document:
    enabled: true
    storage-path: ./data/trust-rag/documents
    max-upload-bytes: 52428800
    max-git-file-bytes: 2097152
    max-git-files: 500
    batch-size: 5
    retry-limit: 3
    fixed-delay-ms: 5000
    keep-source-files: true
    git-remote-enabled: false
    git-allowed-hosts: []
    git-allowed-local-roots:
      - ./data/trust-rag/git-sources
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

当前首次公开版本为完整 V1 基线，已包含三池治理、混合检索、索引补偿、文档导入和评估系统所需的全部表、字段与索引。后续发布后只新增更高版本迁移，不再修改已发布的 V1。

Spring Boot 默认 multipart 上限通常小于 TrustRAG 的文档上限。宿主应用还应同步配置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

远程 Git 默认关闭。启用后必须配置 `git-allowed-hosts`；私有仓库凭据不会保存，也不允许出现在 URL 中。本地 Git 仅允许读取 `git-allowed-local-roots` 下的仓库。

旧配置 `retrieval.low-trust-top-k` 仍可使用，它会同时设置会话、用户、项目和租户四个私有低池配额。新项目建议使用独立配额，避免某一作用域占满整个低池结果集。

## 自定义扩展

声明同类型 Bean 即可覆盖默认实现，包括：

- `LlmClient`、`EmbeddingClient`、`KnowledgeVectorStore`、`KnowledgeKeywordStore`
- `KnowledgeIndexService`、`RrfFusionService`、`IndexSyncTaskRepository`
- `QueryRewriteService`、`RerankClient`
- `PromptBuilder` 或多个 `PromptCustomizer`
- `PrivacyFilter`、`ScopeResolver`、`ScopeClassifier`
- `CandidateExtractor`、`DuplicateDetector`、`ConflictDetector`
- `LlmPreReviewer`、`EvidenceVerifier`、`KnowledgeRelationJudge`
- `ReviewCallback`、`TransactionRunner`

生产环境关闭 `auto-create-collection` 前，应预建与配置维度一致的 Milvus Collection。Embedding 模型发生变化时，应新建 Collection 或执行全量重建索引。
