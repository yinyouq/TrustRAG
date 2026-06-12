# TrustRAG

TrustRAG 是一个面向 Spring Boot 的可信、自进化 RAG Engine。业务系统只调用
`TrustRagEngine.ask()`，框架负责查询改写、分层检索、作用域隔离、可信排序、
Prompt 构造、模型调用、链路记录、知识缺口检测和知识审核闭环。

第一版遵循一个硬约束：模型回答不会直接进入知识库。只有用户明确纠错或人工
提交的内容可以进入低可信候选池，候选必须经人工审核、重新向量化后才能成为
高可信知识。

## 技术版本

| 组件 | 版本 |
|---|---|
| JDK 开发环境 | 21 LTS 推荐 |
| Jar 编译目标 | Java 17 |
| Spring Boot | 3.5.15 |
| Spring AI | 1.1.5 |
| Milvus Java SDK | 2.6.18 |
| Milvus Server | 2.6.7（2.6.x） |
| Maven | 3.9.x |

## 模块

| 模块 | 职责 |
|---|---|
| `trust-rag-core` | 领域模型、SPI、Engine、可信检索、缺口检测、审核状态机 |
| `trust-rag-milvus` | Milvus Collection、过滤表达式、向量读写 |
| `trust-rag-storage-jdbc` | PostgreSQL/MySQL 元数据、Trace、反馈、审核持久化 |
| `trust-rag-admin-api` | 候选、导入、审核和反馈 HTTP API |
| `trust-rag-spring-boot-starter` | 自动配置和 Spring AI 适配 |
| `trust-rag-example-springboot` | H2 + 内存向量库的零外部依赖演示 |
| `trust-rag-docs` | 架构、配置和 API 文档 |

## 构建

```powershell
mvn -s maven-settings.xml clean verify
```

当前工程可在 JDK 17 或 JDK 21 上构建，编译器始终使用 `--release 17`。

## 本地演示

示例默认使用 H2、确定性 Embedding 和本地演示 LLM，不需要 Milvus 或模型密钥。

```powershell
mvn -s maven-settings.xml -pl trust-rag-example-springboot -am package
java -jar trust-rag-example-springboot\target\trust-rag-example-springboot-1.0.0-SNAPSHOT.jar
```

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/chat `
  -ContentType "application/json" `
  -Body '{"question":"TrustRAG 如何处理用户纠错？","userId":"u-1","conversationId":"c-1"}'
```

## Milvus + PostgreSQL

```powershell
docker compose up -d
java -jar trust-rag-example-springboot\target\trust-rag-example-springboot-1.0.0-SNAPSHOT.jar `
  --spring.profiles.active=milvus
```

`milvus` profile 使用 PostgreSQL Flyway 脚本和 64 维演示 Embedding。生产环境应提供
真实的 Spring AI `ChatModel`、`EmbeddingModel` Bean，并让
`trust-rag.milvus.dimension` 与模型维度完全一致。

## 安全默认值

- `trust-rag.enabled` 默认关闭。
- `trust-rag.admin-api.enabled` 默认关闭。
- Prompt 默认不落库。
- 低可信知识禁止使用 `GLOBAL` 作用域。
- `GLOBAL_CANDIDATE` 不参与召回。
- Milvus 过滤后仍会通过关系库进行状态和权限二次校验。
- Admin API 不内置身份系统，生产环境必须由宿主应用使用 Spring Security 等机制保护。

详细说明见 [架构文档](trust-rag-docs/src/main/resources/docs/architecture.md)、
[配置文档](trust-rag-docs/src/main/resources/docs/configuration.md) 和
[API 文档](trust-rag-docs/src/main/resources/docs/api.md)。
