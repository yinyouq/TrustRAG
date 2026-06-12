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
    token:
    username:
    password:
    database: default
    collection: trust_rag_knowledge
    dimension: 1536
    metric-type: COSINE
    auto-create-collection: true
    connect-timeout-ms: 10000
    rpc-deadline-ms: 30000
  retrieval:
    high-trust-top-k: 5
    low-trust-top-k: 3
    min-vector-score: 0.60
  trust-weight:
    high: 1.0
    low-conversation: 0.60
    low-user: 0.45
    low-project: 0.45
    low-tenant: 0.35
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

PostgreSQL 是默认迁移：

```yaml
spring:
  flyway:
    locations: classpath:db/migration
```

MySQL 需要切换位置：

```yaml
spring:
  flyway:
    locations: classpath:db/mysql
```

两套脚本都会创建 `knowledge_item`、`rag_trace`、`retrieval_log`、`feedback` 和
`review_task`。

## 自定义扩展

声明同类型 Bean 即可覆盖默认实现：

- `LlmClient`
- `EmbeddingClient`
- `KnowledgeVectorStore`
- `QueryRewriteService`
- `RerankClient`
- `PromptBuilder` 或多个 `PromptCustomizer`
- `PrivacyFilter`
- `ScopeResolver`
- `ScopeClassifier`
- `CandidateExtractor`
- `ChunkStrategy`
- `ReviewCallback`
- `TransactionRunner`

生产环境关闭 `auto-create-collection` 前，应预先创建与配置维度一致的 Milvus
Collection。Embedding 模型变化时应新建 Collection 或完成全量重建索引。
