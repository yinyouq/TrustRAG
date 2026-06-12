# TrustRAG 架构

## Engine 主流程

```text
RagRequest
  -> QueryRewriteService
  -> ScopeResolver
  -> EmbeddingClient
  -> KnowledgeVectorStore（按可信级别和作用域过滤）
  -> KnowledgeRepository（二次校验状态和权限）
  -> RerankClient（可选）
  -> PromptBuilder
  -> LlmClient
  -> RagTrace + RetrievalLog
  -> KnowledgeGapDetector
  -> RagAnswer
```

业务代码只依赖 `TrustRagEngine`。核心模块不依赖 Spring、Milvus 或 JDBC，所有外部
能力通过 SPI 注入。

## 可信分层

- `HIGH`：人工审核或可信来源导入，可作为主要回答依据。
- `LOW`：用户纠错和候选知识，只能在匹配的私有作用域中辅助召回。
- `MEDIUM`：保留枚举和扩展位置，第一版不主动使用。

低可信知识不能使用 `GLOBAL`。待审核的公共候选使用 `GLOBAL_CANDIDATE`，该作用域
永不参与检索；审核通过后才转换为 `GLOBAL` 和 `HIGH`。

## 状态机

```text
文档导入:
INDEXING -> ENABLED
         -> INDEX_FAILED -> retryIndex()

用户纠错:
PENDING_REVIEW -> INDEXING -> ENABLED
               -> INDEXING -> INDEX_FAILED -> approve() 重试
               -> REJECTED
```

审批使用 `status + version` 条件更新，避免并发审核重复晋升。候选与审核任务、反馈
与候选、审核结果与审核任务均通过 `TransactionRunner` 在关系库事务中提交。

Milvus 不参与关系库事务。框架先写 `INDEXING`，向量成功后再原子更新为 `ENABLED`；
失败时写入 `INDEX_FAILED`。即使 Milvus 存在残留向量，关系库二次校验也会阻止召回。

## 知识飞轮

`ask()` 只记录缺口，不把模型答案自动学习。可进入候选池的来源是：

1. 用户提交 `CORRECTION` 且提供 `correctedAnswer`。
2. 管理员手动创建候选。
3. 管理员导入经过确认的高可信文档。

审核通过时重新计算 Embedding 并覆盖 Milvus 元数据，然后知识才能被召回。

## 安全边界

- 向量检索使用服务端构造的作用域过滤表达式。
- 用户、会话、项目和租户标识使用 Milvus 模板参数，不拼接到表达式。
- Prompt 将检索内容标记为资料，明确忽略知识片段中的指令。
- 默认隐私过滤器脱敏 API Key、JDBC URL 和中国大陆手机号。
- 完整 Prompt 默认不持久化。
- 管理 API 默认关闭，认证与授权由宿主系统负责。
