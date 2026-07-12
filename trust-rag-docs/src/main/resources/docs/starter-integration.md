# TrustRAG Starter 外部项目接入指南

本文档说明在已经执行过 `mvn clean install` 之后，如何在另一个 Spring Boot / Agent 项目中引入 TrustRAG，并把它作为项目的 RAG 能力使用。

## 1. 接入目标

你的业务项目接入 TrustRAG 后，可以获得以下能力：

- 通过 `TrustRagEngine` 完成可信 RAG 问答。
- 通过 `KnowledgeIngestionService` 导入高、中、低可信知识。
- 通过 `TrustRagFeedbackService` 接收用户纠错和反馈。
- 通过管理 API 处理知识审核、晋升治理、冲突记录和评估任务。
- 可选接入 `trust-rag-admin-ui` 作为评估管理台。

整体结构如下：

```text
你的 Agent / 业务系统
  -> 引入 trust-rag-spring-boot-starter
  -> 提供 DataSource、模型、Embedding、向量库配置
  -> 注入 TrustRagEngine / KnowledgeIngestionService / TrustRagFeedbackService
  -> 调用 TrustRAG 完成问答、导入、反馈和治理
```

## 2. 前置条件

建议环境：

- JDK 17 或以上。
- Spring Boot 3.x，建议与 TrustRAG 当前依赖的 Spring Boot 3.5.x 保持接近。
- Maven 3.9.x。
- 已在 TrustRAG 工程执行过：

```powershell
mvn clean install
```

如果你执行的是：

```powershell
mvn -s maven-settings.xml "-Dmaven.repo.local=D:\TrustRAG\.mvn-local" clean install
```

那么你的另一个项目构建时也需要使用同一个本地仓库参数，否则 Maven 会去默认 `~/.m2` 查找依赖，可能找不到 TrustRAG。

更推荐把 TrustRAG 安装到默认 Maven 本地仓库，或发布到 Nexus、GitHub Packages、私有 Maven 仓库。

## 3. 在业务项目中引入依赖

在你的业务项目 `pom.xml` 中加入：

```xml
<dependency>
    <groupId>io.github.trustrag</groupId>
    <artifactId>trust-rag-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

如果你的业务项目需要暴露 HTTP 接口或启用 TrustRAG 管理 API，还需要有 Web 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

如果你使用 PostgreSQL：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

如果你使用 MySQL：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

`trust-rag-spring-boot-starter` 会传递引入 core、Milvus、OpenSearch、Document、Evaluation、JDBC Storage 和 Admin API 等模块。

## 4. 最小可运行配置

推荐先使用 MySQL + Milvus + OpenSearch 跑通真实链路，这与仓库默认的 `docker-compose.yml` 保持一致。

`application.yml` 示例：

```yaml
spring:
  application:
    name: your-agent-app
  datasource:
    url: jdbc:mysql://localhost:3306/trust_rag?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: trust_rag
    password: trust_rag
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
    locations: classpath:db/mysql
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

trust-rag:
  enabled: true

  admin-api:
    enabled: true

  milvus:
    enabled: true
    uri: http://localhost:19530
    database: default
    collection: trust_rag_knowledge_vector
    dimension: 1536
    metric-type: COSINE
    vector-top-k: 30
    auto-create-collection: true

  opensearch:
    enabled: true
    uris:
      - http://localhost:9200
    index-name: trust_rag_knowledge_keyword
    keyword-top-k: 30
    auto-create-index: true

  retrieval:
    mode: hybrid-rrf
    prompt-top-k: 5
    fusion-top-n: 30
    min-vector-score: 0.60

  rrf:
    enabled: true
    k: 60

  trace:
    save-prompt: false
```

注意：`trust-rag.milvus.dimension` 必须与你使用的 Embedding 模型输出维度一致。例如：

- OpenAI `text-embedding-3-small` 常见维度可以配置为 1536。
- 如果你的 Embedding 模型输出 1024 维，这里就必须填 1024。
- 模型维度变化后，应新建 Milvus Collection 或重建索引。

## 5. 数据库配置

### PostgreSQL

TrustRAG 的 PostgreSQL Flyway 脚本位于：

```text
classpath:db/migration
```

配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### MySQL

TrustRAG 的 MySQL Flyway 脚本位于：

```text
classpath:db/mysql
```

配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/mysql
```

### 已有 Flyway 迁移目录的项目

如果你的项目原本就有业务数据库迁移脚本，可以同时配置多个目录：

```yaml
spring:
  flyway:
    enabled: true
    locations:
      - classpath:db/migration
      - classpath:db/your-app
```

如果你的业务库和 TrustRAG 库想分开，建议使用独立数据源或独立 schema。当前 starter 默认使用 Spring 容器中的主 `DataSource`。

## 6. 外部组件准备

完整模式建议准备：

- MySQL 或 PostgreSQL：保存知识元数据、Trace、反馈、治理任务和评估数据。
- Milvus：保存知识向量。
- OpenSearch：保存关键词索引。
- LLM：用于最终回答、候选知识预审和冲突判断。
- Embedding 模型：用于导入知识和查询向量化。

如果你只是本地试跑，可以参考 TrustRAG 工程根目录的 `docker-compose.yml`，启动 MySQL、Milvus 和 OpenSearch：

```powershell
docker compose up -d
```

这个 Compose 文件默认使用 MySQL；如需 PostgreSQL，请自行提供 PostgreSQL 服务并将 Flyway 路径切换为 `classpath:db/migration`。

## 7. 提供模型能力

TrustRAG 至少需要两个模型能力：

- `LlmClient` 或 Spring AI `ChatModel`
- `EmbeddingClient` 或 Spring AI `EmbeddingModel`

### 方式一：使用 Spring AI

如果你的项目已经使用 Spring AI，只要 Spring 容器里有 `ChatModel` 和 `EmbeddingModel`，starter 会自动适配成 TrustRAG 的 `LlmClient` 和 `EmbeddingClient`。

伪配置示例：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4.1-mini
      embedding:
        options:
          model: text-embedding-3-small
```

不同模型供应商的 Spring AI 配置项可能不同，以你项目实际依赖为准。

### 方式二：自己实现 TrustRAG SPI

如果你的 agent 项目已经有自己的模型调用封装，可以直接注册 `LlmClient`：

```java
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.spi.LlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TrustRagModelConfiguration {

    @Bean
    LlmClient trustRagLlmClient(YourAgentModelClient modelClient) {
        return prompt -> {
            String answer = modelClient.chat(prompt);
            return new LlmResponse(answer, 0.80, new TokenUsage(0, 0));
        };
    }
}
```

注册 `EmbeddingClient`：

```java
import io.github.trustrag.core.spi.EmbeddingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class TrustRagEmbeddingConfiguration {

    @Bean
    EmbeddingClient trustRagEmbeddingClient(YourEmbeddingClient embeddingClient) {
        return new EmbeddingClient() {
            @Override
            public List<Float> embed(String text) {
                return embeddingClient.embed(text);
            }

            @Override
            public String modelName() {
                return "your-embedding-model";
            }

            @Override
            public int dimension() {
                return 1536;
            }
        };
    }
}
```

`dimension()` 的返回值必须和 `trust-rag.milvus.dimension` 一致。

## 8. 在业务代码中使用 TrustRAG

### 8.1 问答

注入 `TrustRagEngine`：

```java
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.service.TrustRagEngine;
import org.springframework.stereotype.Service;

@Service
public class AgentRagService {

    private final TrustRagEngine trustRagEngine;

    public AgentRagService(TrustRagEngine trustRagEngine) {
        this.trustRagEngine = trustRagEngine;
    }

    public RagAnswer ask(String question, String userId, String conversationId) {
        return trustRagEngine.ask(RagRequest.builder()
                .question(question)
                .userId(userId)
                .conversationId(conversationId)
                .build());
    }
}
```

如果你的系统有项目、租户概念，建议同时传入：

```java
.projectId(projectId)
.tenantId(tenantId)
```

TrustRAG 会用这些字段控制低可信知识的可见范围。

### 8.2 对外暴露自己的聊天接口

```java
import io.github.trustrag.core.model.RagAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/chat")
public class AgentChatController {

    private final AgentRagService ragService;

    public AgentChatController(AgentRagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public RagAnswer chat(@Valid @RequestBody ChatRequest request) {
        return ragService.ask(
                request.question(),
                request.userId(),
                request.conversationId());
    }

    public record ChatRequest(
            @NotBlank String question,
            String userId,
            String conversationId) {
    }
}
```

### 8.3 导入知识

注入 `KnowledgeIngestionService`：

```java
import io.github.trustrag.core.model.KnowledgeImportRequest;
import io.github.trustrag.core.model.KnowledgeImportResult;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import org.springframework.stereotype.Service;

@Service
public class AgentKnowledgeService {

    private final KnowledgeIngestionService ingestionService;

    public AgentKnowledgeService(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public KnowledgeImportResult importOfficialDoc(String title, String content, String sourceRef) {
        return ingestionService.importKnowledge(new KnowledgeImportRequest(
                title,
                content,
                "official_doc",
                sourceRef,
                TrustLevel.HIGH,
                ScopeType.GLOBAL,
                null,
                null,
                null,
                null));
    }
}
```

常见信任级别：

- `HIGH`：高可信知识，可直接用于全局回答。
- `MEDIUM`：中可信知识，通常需要人工复核。
- `LOW`：低可信知识，适合用户纠错、会话经验、候选知识。

常见作用域：

- `GLOBAL`：全局可见。
- `TENANT`：租户内可见。
- `PROJECT`：项目内可见。
- `USER`：用户内可见。
- `CONVERSATION`：会话内可见。
- `GLOBAL_CANDIDATE`：公共候选，默认不作为正式全局知识召回。

低可信知识不能使用 `GLOBAL` 作用域。

### 8.4 提交反馈和纠错

注入 `TrustRagFeedbackService`：

```java
import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import org.springframework.stereotype.Service;

@Service
public class AgentFeedbackService {

    private final TrustRagFeedbackService feedbackService;

    public AgentFeedbackService(TrustRagFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public void correction(String traceId, String correctedAnswer, String userId, String conversationId) {
        feedbackService.submitFeedback(new RagFeedbackRequest(
                traceId,
                FeedbackType.CORRECTION,
                "用户提交了纠错",
                correctedAnswer,
                userId,
                conversationId,
                null,
                null));
    }
}
```

`CORRECTION` 必须提供 `correctedAnswer`。纠错会进入低可信候选池，再经过治理流程晋升。

## 9. 启用管理 API

配置：

```yaml
trust-rag:
  enabled: true
  admin-api:
    enabled: true
```

启用后常见接口包括：

```text
POST /trust-rag/admin/knowledge/import
GET  /trust-rag/admin/knowledge
GET  /trust-rag/admin/knowledge/candidates
POST /trust-rag/admin/knowledge/{id}/approve-high
POST /trust-rag/admin/knowledge/{id}/reject
POST /trust-rag/admin/promotion/run
POST /trust-rag/feedback
POST /api/documents/upload
POST /api/documents/git
GET  /trust-rag/admin/eval/datasets
POST /trust-rag/admin/eval/runs
```

生产环境必须为 `/trust-rag/**` 配置认证和权限控制。TrustRAG 不负责签发登录态，也不内置 RBAC。

## 10. 接入 admin-ui

`trust-rag-admin-ui` 是独立 Vue 前端，主要用于评估管理和治理指标展示。

开发模式连接你的业务后端：

```powershell
cd D:\TrustRAG\trust-rag-admin-ui
$env:VITE_USE_MOCKS='false'
$env:VITE_API_BASE_URL='/trust-rag/admin/eval'
corepack pnpm dev
```

如果你的业务后端不是 `http://localhost:8080`，需要修改 `trust-rag-admin-ui/vite.config.ts` 中的代理目标。

生产部署：

```powershell
cd D:\TrustRAG\trust-rag-admin-ui
corepack pnpm build
```

然后把 `dist/` 交给 Nginx、网关或你的 Spring Boot 应用托管，并把 `/trust-rag/admin/eval/**` 转发到业务后端。

注意：当前 admin-ui 主要是评估管理台，不是聊天页面，也不是完整知识库管理后台。

## 11. 不想先安装 Milvus/OpenSearch 怎么办

在外部项目中，starter 默认会根据配置创建 Milvus 和 OpenSearch 实现。

如果你暂时不想安装它们，可以：

1. 关闭 Milvus / OpenSearch。
2. 自己提供 `KnowledgeVectorStore`。
3. 可选提供 `KnowledgeKeywordStore`，否则关闭关键词检索或使用 no-op 实现。

示例：

```yaml
trust-rag:
  enabled: true
  milvus:
    enabled: false
  opensearch:
    enabled: false
  retrieval:
    mode: vector
```

然后注册自己的向量库实现：

```java
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class LocalVectorStoreConfiguration {

    @Bean
    KnowledgeVectorStore knowledgeVectorStore() {
        return new KnowledgeVectorStore() {
            @Override
            public void initialize() {
            }

            @Override
            public void upsert(KnowledgeItem knowledge, List<Float> vector) {
                throw new UnsupportedOperationException("请替换为你的向量存储实现");
            }

            @Override
            public void delete(long knowledgeId) {
                throw new UnsupportedOperationException("请替换为你的向量存储实现");
            }

            @Override
            public List<VectorHit> search(VectorSearchRequest request) {
                throw new UnsupportedOperationException("请替换为你的向量存储实现");
            }
        };
    }
}
```

真实项目不要直接使用上面的 `UnsupportedOperationException`，它只是说明需要你替换实现。你也可以参考 `trust-rag-example-springboot` 中的内存向量库写法。

## 12. 常见错误

### 12.1 找不到依赖

现象：

```text
Could not find artifact io.github.trustrag:trust-rag-spring-boot-starter
```

处理：

- 确认已在 TrustRAG 根目录执行 `mvn clean install`。
- 如果安装到了 `.mvn-local`，业务项目也要加同样的 `-Dmaven.repo.local`。
- 确认版本号是 `1.0.0-SNAPSHOT`。

### 12.2 `TrustRagEngine` 没有 Bean

常见原因：

- 没有配置 `trust-rag.enabled=true`。
- 缺少 `DataSource`。
- 缺少 `LlmClient` / `ChatModel`。
- 缺少 `EmbeddingClient` / `EmbeddingModel`。
- Milvus 开启但连接失败，导致自动配置失败。

### 12.3 Milvus 维度不一致

现象：

```text
Vector dimension mismatch
```

处理：

- 检查 `trust-rag.milvus.dimension`。
- 检查 `EmbeddingClient.dimension()` 或 Spring AI Embedding 模型输出维度。
- 如果 Collection 已经创建过，需要删除旧 Collection 或新建 Collection。

### 12.4 Flyway 找不到表或启动后 SQL 报错

处理：

- PostgreSQL 使用 `classpath:db/migration`。
- MySQL 使用 `classpath:db/mysql`。
- 确认业务项目引入了对应数据库驱动。
- 确认 TrustRAG 依赖已经被 Maven 正确解析到 classpath。

### 12.5 admin-ui 请求 404

处理：

- 确认后端配置了 `trust-rag.admin-api.enabled=true`。
- 确认业务项目是 Servlet Web 应用。
- 确认前端 `VITE_API_BASE_URL` 指向 `/trust-rag/admin/eval`。
- 确认网关或 Vite proxy 把 `/trust-rag` 转发到你的业务后端。

## 13. 推荐落地顺序

1. 在 TrustRAG 工程执行 `mvn clean install`。
2. 在业务项目引入 `trust-rag-spring-boot-starter`。
3. 配置 `trust-rag.enabled=true`。
4. 配好数据库和 Flyway。
5. 提供 `LlmClient` / `EmbeddingClient`，或 Spring AI `ChatModel` / `EmbeddingModel`。
6. 启动 Milvus 和 OpenSearch，配置连接地址。
7. 启动业务项目，确认 `TrustRagEngine` Bean 可注入。
8. 写一个业务 `/chat` 接口调用 `TrustRagEngine.ask()`。
9. 导入一条高可信知识，验证问答能召回。
10. 打开 `trust-rag.admin-api.enabled=true`。
11. 可选启动 `trust-rag-admin-ui` 连接评估接口。

## 14. 最小验收清单

接入完成后，至少验证：

- 应用能正常启动。
- Flyway 已创建 TrustRAG 相关表。
- `TrustRagEngine` 能注入。
- 能成功导入一条知识。
- 导入知识后 Milvus / 自定义向量库有对应向量。
- 问答接口能返回 `RagAnswer`。
- `RagAnswer` 中能看到 trace 或使用知识信息。
- 提交 `CORRECTION` 反馈后能生成低可信候选。
- 如果启用了 admin API，`GET /trust-rag/admin/knowledge` 能返回数据。
