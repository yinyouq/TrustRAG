# TrustRAG

TrustRAG 是一个面向 Spring Boot 的可信、自进化 RAG 框架。它不仅完成检索增强生成，还通过低、中、高三类知识池、自动晋升、冲突检测、人工终审、反馈回流和生命周期治理，控制知识如何进入、使用、升级、降级和退出。

模型回答不会直接写入知识库。只有用户纠错、人工提交或可信文档导入能够产生知识；低池知识必须经过自动治理进入中池，再由人工审批进入高池。

## 技术版本

| 组件 | 版本 |
|---|---|
| JDK 开发环境 | 21 LTS 推荐 |
| Jar 编译目标 | Java 17 |
| Spring Boot | 3.5.15 |
| Spring AI | 1.1.5 |
| Milvus Java SDK | 2.6.18 |
| Milvus Server | 2.6.x 稳定版 |
| OpenSearch Java Client | 2.19.0 |
| OpenSearch Server | 2.19.x |
| Maven | 3.9.x |

## 三池流程

```text
用户纠错/人工候选
  -> LOW_PENDING
  -> 去重、隐私、LLM 预审、来源与证据评分、冲突检测
  -> HUMAN_REVIEW_PENDING (MEDIUM)
  -> 人工终审
  -> HIGH_ENABLED
```

不满足阈值的知识保留在 `LOW_ENABLED`；严重隐私风险、冲突、过期或重复内容分别进入 `REJECTED`、`CONFLICT`、`EXPIRED` 或 `MERGE_PENDING`。

## 模块

| 模块 | 职责 |
|---|---|
| `trust-rag-core` | 领域模型、三池检索、提示词、缺口检测、晋升与生命周期 |
| `trust-rag-milvus` | Milvus 2.6 向量读写与服务端作用域过滤 |
| `trust-rag-opensearch` | OpenSearch BM25、索引映射与服务端权限过滤 |
| `trust-rag-document` | 文档上传、PDF/Word/HTML/Markdown/Wiki 解析、Tika 兜底与 Git 仓库读取 |
| `trust-rag-evaluation` | 测试集、检索指标、LLM Judge、Before/After 与治理快照 |
| `trust-rag-storage-jdbc` | PostgreSQL/MySQL 元数据、Trace、反馈和治理任务 |
| `trust-rag-admin-api` | 导入、晋升、冲突、审批、降级、回滚和合并 API |
| `trust-rag-admin-ui` | Vue 3 + ECharts 评估管理台 |
| `trust-rag-spring-boot-starter` | Spring AI 适配与自动配置 |
| `trust-rag-example-springboot` | H2 + 内存向量库的完整三池演示 |
| `trust-rag-docs` | 架构、配置和 API 文档 |

## 构建与验证

```powershell
mvn -s maven-settings.xml clean verify
```

项目可在 JDK 17 或 JDK 21 上构建，编译目标始终为 Java 17。

评估管理台单独构建：

```powershell
cd trust-rag-admin-ui
corepack pnpm install --frozen-lockfile
corepack pnpm test:run
corepack pnpm build
```

开发时复制 `.env.example` 中需要的变量，执行 `corepack pnpm dev`。设置 `VITE_USE_MOCKS=true` 可在不启动后端的情况下体验完整评估流程；生产构建默认不会加载 Mock。

## 本地演示

```powershell
mvn -s maven-settings.xml -pl trust-rag-example-springboot -am package
java -jar trust-rag-example-springboot\target\trust-rag-example-springboot-1.0.0-SNAPSHOT.jar
```

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/chat `
  -ContentType "application/json" `
  -Body '{"question":"TrustRAG 如何处理用户纠错？","userId":"u-1","conversationId":"c-1"}'
```

Milvus、OpenSearch 与 PostgreSQL 混合检索模式：

```powershell
docker compose up -d
java -jar trust-rag-example-springboot\target\trust-rag-example-springboot-1.0.0-SNAPSHOT.jar `
  --spring.profiles.active=milvus
```

生产环境应提供真实的 Spring AI `ChatModel`、`EmbeddingModel`，并确保 `trust-rag.milvus.dimension` 与 Embedding 维度完全一致。默认检索模式为 Milvus 与 OpenSearch 各召回 30 条，使用 `k=60` 的 RRF 融合后保留 30 条，再选取前 5 条进入 Prompt。

## 安全默认值

- `trust-rag.enabled` 和管理 API 默认关闭。
- 完整 Prompt 默认不持久化。
- 低可信知识禁止使用 `GLOBAL`。
- `GLOBAL_CANDIDATE` 默认不参与召回。
- Milvus 和 OpenSearch 都执行服务端状态与作用域过滤，关系库还会进行二次校验。
- 任一检索引擎故障时自动降级为单路召回；两路都故障时进入无上下文和知识缺口流程。
- 双索引写入失败会生成 `index_sync_task`，补偿成功前知识保持 `INDEX_FAILED`。
- 管理 API 不内置身份系统，生产环境必须由宿主应用保护。

详细说明见[架构文档](trust-rag-docs/src/main/resources/docs/architecture.md)、[配置文档](trust-rag-docs/src/main/resources/docs/configuration.md)和 [API 文档](trust-rag-docs/src/main/resources/docs/api.md)。
