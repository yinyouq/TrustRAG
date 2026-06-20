# TrustRAG OpenSearch + Milvus + RRF 混合检索设计文档

> 设计目标：将当前只有 Milvus 向量检索的 TrustRAG 检索层升级为 **OpenSearch BM25 + Milvus Vector + 应用层 RRF 融合** 的稳定混合检索架构。  
> 技术路线固定：**OpenSearch 做关键词检索，Milvus 做向量检索，应用层使用 RRF 做结果融合**。  
> 不采用 PostgreSQL Full Text Search，不采用 Elasticsearch，不采用加权融合优先方案。  
> 当前问题：`retrieval_log.keyword_score` 始终为 `null`，检索类型固定为 `VECTOR`，系统没有真正的 BM25、Hybrid Search、RRF 和关键词得分。

---

## 1. 技术选型固定版

本设计只采用一套明确方案。

| 模块 | 技术选型 | 作用 |
|---|---|---|
| 后端框架 | Spring Boot 3.5.x | TrustRAG Starter 主体 |
| AI 编排 | Spring AI 1.1.x | LLM、Embedding 接入 |
| 向量数据库 | Milvus 2.6.x | 语义向量检索 |
| Milvus Java SDK | 2.6.x，建议对齐 2.6.20 | Java 访问 Milvus |
| 关键词检索 | OpenSearch 2.19+ | BM25 关键词检索 |
| 混合融合 | 应用层 RRF | 融合 Milvus 与 OpenSearch 结果 |
| 元数据数据库 | PostgreSQL 16/17 或 MySQL 8 | 存知识、Trace、日志、审核等业务数据 |
| 异步任务 | Spring Scheduling，后续可接 RabbitMQ | 索引同步、失败重试 |
| 文档解析 | 另一个模块处理，不在本设计展开 | 本文只设计检索层 |

说明：

1. **Milvus 只做向量检索**，不承担关键词搜索、审核状态、版本管理等业务职责。
2. **OpenSearch 只做关键词检索**，不承担向量库职责。
3. **RRF 在应用层实现**，不依赖 OpenSearch 内置 RRF 管道，方便统一处理权限、可信等级、日志和后续 Rerank。
4. **关系型数据库仍然是主元数据存储**，Milvus 和 OpenSearch 都是检索索引。

---

## 2. 为什么必须做 Hybrid Search

当前只有 Milvus 向量检索，适合语义相似问题，例如：

```text
“Spring AI 工具调用怎么实现？”
“如何让 RAG 系统自动补充知识？”
```

但向量检索在以下场景容易不稳定：

```text
错误码
类名
方法名
接口路径
配置项
日志关键字
英文缩写
产品型号
数据库字段名
精确版本号
```

例如：

```text
BeanDefinitionOverrideException
ToolCallbackProvider
/api/v1/knowledge/import
trust-rag.gap-detection.enabled
MilvusException code=1100
```

这些问题更适合 BM25 关键词检索。  
因此，完整检索层应该同时具备：

```text
Milvus Vector Search：语义召回
OpenSearch BM25 Search：关键词召回
RRF：融合两个召回列表
Rerank：可选二次精排
```

最终目标：

```text
语义问题能召回
关键词问题能召回
专有名词能召回
日志错误能召回
接口名、类名、配置项能召回
```

---

## 3. 总体架构

### 3.1 检索架构图

```text
用户问题
  ↓
Query Rewrite
  ↓
Embedding
  ↓
┌─────────────────────┬─────────────────────┐
│ Milvus Vector Search │ OpenSearch BM25      │
│ 语义召回             │ 关键词召回            │
└─────────────────────┴─────────────────────┘
              ↓
        Result Normalize
              ↓
           RRF Fusion
              ↓
        Metadata Enrich
              ↓
     Trust / Scope 校验
              ↓
          可选 Rerank
              ↓
        Prompt TopK 选择
              ↓
          LLM 回答
              ↓
      retrieval_log 记录
```

### 3.2 数据存储职责

```text
PostgreSQL / MySQL
  - knowledge_item
  - rag_trace
  - retrieval_log
  - review_task
  - feedback
  - gap_event
  - promotion_task

Milvus
  - knowledge_id
  - embedding
  - trust_level
  - scope_type
  - tenant_id
  - project_id
  - user_id
  - conversation_id
  - status

OpenSearch
  - knowledge_id
  - title
  - claim
  - content
  - tags
  - trust_level
  - scope_type
  - tenant_id
  - project_id
  - user_id
  - conversation_id
  - status
```

核心原则：

> 关系型数据库是事实源，Milvus 和 OpenSearch 是索引副本。

---

## 4. 模块划分

建议新增或重构以下模块。

```text
trust-rag-core
  - HybridRetriever 抽象
  - RrfFusion
  - RetrievedChunk
  - RetrievalScore

trust-rag-milvus
  - MilvusVectorSearchClient
  - MilvusIndexSyncService

trust-rag-opensearch
  - OpenSearchKeywordSearchClient
  - OpenSearchIndexSyncService
  - OpenSearchIndexInitializer

trust-rag-retrieval
  - DefaultHybridRetriever
  - RetrievalLogService
  - SearchResultMerger
```

### 4.1 主要类关系

```text
TrustRagEngine
  ↓
TrustAwareRetriever
  ↓
DefaultHybridRetriever
  ├── MilvusVectorSearchClient
  ├── OpenSearchKeywordSearchClient
  ├── RrfFusion
  ├── KnowledgeMetadataLoader
  └── RetrievalLogService
```

---

## 5. OpenSearch 索引设计

### 5.1 索引名称

```text
trust_rag_knowledge_keyword
```

如果支持多环境：

```text
trust_rag_knowledge_keyword_dev
trust_rag_knowledge_keyword_test
trust_rag_knowledge_keyword_prod
```

不建议按租户建多个索引。  
推荐所有租户共用一个索引，通过 `tenant_id` 做 filter。

原因：

```text
1. 索引数量少，维护简单
2. 查询时统一做权限过滤
3. 后续统计和重建更方便
4. 防止租户多时索引爆炸
```

---

### 5.2 Mapping 设计

```json
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "analysis": {
      "analyzer": {
        "trust_rag_text_analyzer": {
          "type": "standard"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "knowledge_id": {
        "type": "long"
      },
      "title": {
        "type": "text",
        "analyzer": "trust_rag_text_analyzer"
      },
      "claim": {
        "type": "text",
        "analyzer": "trust_rag_text_analyzer"
      },
      "content": {
        "type": "text",
        "analyzer": "trust_rag_text_analyzer"
      },
      "tags": {
        "type": "keyword"
      },
      "source_type": {
        "type": "keyword"
      },
      "trust_level": {
        "type": "keyword"
      },
      "scope_type": {
        "type": "keyword"
      },
      "tenant_id": {
        "type": "keyword"
      },
      "project_id": {
        "type": "keyword"
      },
      "user_id": {
        "type": "keyword"
      },
      "conversation_id": {
        "type": "keyword"
      },
      "status": {
        "type": "keyword"
      },
      "created_at": {
        "type": "date"
      },
      "updated_at": {
        "type": "date"
      }
    }
  }
}
```

### 5.3 中文分词说明

第一版先使用 OpenSearch 标准 analyzer。  
后续如果中文关键词检索效果不够，再加入中文分词插件。

原因：

```text
1. 第一版先把 OpenSearch + RRF 主链路跑通
2. 中文分词插件会增加部署复杂度
3. 标准 analyzer 对英文类名、接口名、错误码已经有价值
4. 中文效果可以后续通过 analyzer 优化
```

---

## 6. OpenSearch BM25 查询设计

### 6.1 查询字段权重

字段权重固定为：

```text
title^3
claim^2
content^1
tags^2
```

说明：

| 字段 | 权重 | 原因 |
|---|---:|---|
| title | 3 | 标题命中通常相关性最高 |
| claim | 2 | 知识断言命中价值高 |
| tags | 2 | 标签一般是主题关键词 |
| content | 1 | 正文范围大，权重最低 |

---

### 6.2 权限过滤条件

OpenSearch 查询必须和 Milvus 一样执行权限过滤。

逻辑：

```text
status = enabled
AND
(
  scope_type = global
  OR tenant_id = 当前 tenantId
  OR project_id = 当前 projectId
  OR user_id = 当前 userId
  OR conversation_id = 当前 conversationId
)
```

注意：

> 不能先查出结果再过滤，因为中间日志、highlight、debug 信息也可能泄露数据。

---

### 6.3 OpenSearch 查询示例

```json
{
  "size": 20,
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "Spring AI ToolCallback 动态注册",
            "fields": [
              "title^3",
              "claim^2",
              "tags^2",
              "content"
            ],
            "type": "best_fields"
          }
        }
      ],
      "filter": [
        {
          "term": {
            "status": "enabled"
          }
        },
        {
          "bool": {
            "should": [
              {
                "term": {
                  "scope_type": "global"
                }
              },
              {
                "term": {
                  "tenant_id": "t_001"
                }
              },
              {
                "term": {
                  "project_id": "p_001"
                }
              },
              {
                "term": {
                  "user_id": "u_001"
                }
              },
              {
                "term": {
                  "conversation_id": "c_001"
                }
              }
            ],
            "minimum_should_match": 1
          }
        }
      ]
    }
  },
  "highlight": {
    "fields": {
      "title": {},
      "claim": {},
      "content": {}
    }
  }
}
```

---

## 7. Milvus 向量检索设计

### 7.1 Milvus Collection

Collection 名称：

```text
trust_rag_knowledge_vector
```

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Int64 | Milvus 主键 |
| knowledge_id | Int64 | 对应关系库知识 ID |
| embedding | FloatVector | 向量 |
| trust_level | VarChar | high / medium / low |
| scope_type | VarChar | global / tenant / project / user / conversation |
| tenant_id | VarChar | 租户 ID |
| project_id | VarChar | 项目 ID |
| user_id | VarChar | 用户 ID |
| conversation_id | VarChar | 会话 ID |
| status | VarChar | enabled / rejected / expired / index_failed |
| created_at | Int64 | 时间戳 |

---

### 7.2 Milvus 检索过滤条件

Milvus 也必须带权限过滤：

```text
status == "enabled" &&
(
  scope_type == "global"
  || tenant_id == "t_001"
  || project_id == "p_001"
  || user_id == "u_001"
  || conversation_id == "c_001"
)
```

---

### 7.3 检索参数

建议配置：

```yaml
trust-rag:
  milvus:
    collection: trust_rag_knowledge_vector
    dimension: 1536
    metric-type: COSINE
    vector-top-k: 30
```

说明：

```text
vector-top-k 不应该等于最终 prompt topK。
Milvus 初召回应取大一些，例如 30。
经过 RRF、去重、可信度处理、Rerank 后再取最终 topK。
```

---

## 8. 应用层 RRF 融合设计

### 8.1 为什么在应用层做 RRF

虽然 OpenSearch 已经支持 RRF 相关能力，但本框架选择在应用层实现 RRF。

原因：

```text
1. Milvus 和 OpenSearch 是两个独立系统
2. 应用层可以统一处理权限、可信等级、Trace、日志
3. RRF 结果需要写入 retrieval_log
4. 后续要结合 trust_score、freshness_score、feedback_score
5. 便于调试每个知识的 vector_rank 和 keyword_rank
```

---

### 8.2 RRF 公式

```text
rrf_score = 1 / (k + vector_rank) + 1 / (k + keyword_rank)
```

固定参数：

```text
k = 60
```

如果某条知识只出现在向量结果中：

```text
rrf_score = 1 / (60 + vector_rank)
```

如果只出现在关键词结果中：

```text
rrf_score = 1 / (60 + keyword_rank)
```

如果同时出现在两边：

```text
rrf_score = 1 / (60 + vector_rank) + 1 / (60 + keyword_rank)
```

---

### 8.3 排名示例

向量结果：

```text
A rank=1
B rank=2
C rank=3
```

关键词结果：

```text
B rank=1
D rank=2
A rank=3
```

RRF：

```text
A = 1/(60+1) + 1/(60+3)
B = 1/(60+2) + 1/(60+1)
C = 1/(60+3)
D = 1/(60+2)
```

B 和 A 因为同时被两种检索召回，会排在更前面。

---

### 8.4 RRF 融合对象

```java
public class HybridCandidate {

    private Long knowledgeId;

    private Double vectorScore;
    private Integer vectorRank;

    private Double keywordScore;
    private Integer keywordRank;

    private Double rrfScore;

    private String trustLevel;
    private String scopeType;

    private Double trustScore;
    private Double finalScore;
}
```

---

### 8.5 RRF 实现接口

```java
public interface HybridFusionService {

    List<HybridCandidate> fuse(VectorSearchResult vectorResult,
                               KeywordSearchResult keywordResult);
}
```

默认实现：

```java
public class RrfHybridFusionService implements HybridFusionService {

    private final int rrfK;

    @Override
    public List<HybridCandidate> fuse(VectorSearchResult vectorResult,
                                      KeywordSearchResult keywordResult) {
        Map<Long, HybridCandidate> map = new HashMap<>();

        for (int i = 0; i < vectorResult.getHits().size(); i++) {
            VectorHit hit = vectorResult.getHits().get(i);
            HybridCandidate candidate = map.computeIfAbsent(
                    hit.getKnowledgeId(),
                    HybridCandidate::new
            );
            candidate.setVectorScore(hit.getVectorScore());
            candidate.setVectorRank(i + 1);
        }

        for (int i = 0; i < keywordResult.getHits().size(); i++) {
            KeywordHit hit = keywordResult.getHits().get(i);
            HybridCandidate candidate = map.computeIfAbsent(
                    hit.getKnowledgeId(),
                    HybridCandidate::new
            );
            candidate.setKeywordScore(hit.getKeywordScore());
            candidate.setKeywordRank(i + 1);
        }

        for (HybridCandidate candidate : map.values()) {
            double score = 0.0;
            if (candidate.getVectorRank() != null) {
                score += 1.0 / (rrfK + candidate.getVectorRank());
            }
            if (candidate.getKeywordRank() != null) {
                score += 1.0 / (rrfK + candidate.getKeywordRank());
            }
            candidate.setRrfScore(score);
        }

        return map.values().stream()
                .sorted(Comparator.comparing(HybridCandidate::getRrfScore).reversed())
                .toList();
    }
}
```

---

## 9. 可信度与 RRF 的关系

### 9.1 不再强制 high 永远排在前面

当前问题：如果最终排序优先按可信等级，可能导致：

```text
相关性较低的 high 排在前面
相关性很高的 medium 被压到后面
```

新策略：

```text
召回排序主要看 RRF 和 Rerank
事实裁决看 trust_level
Prompt 中显式标记可信等级
```

也就是说：

```text
排序阶段：允许 medium 因相关性高进入上下文
生成阶段：如果 medium 与 high 冲突，必须优先 high
```

---

### 9.2 final_score 设计

RRF 之后，增加轻量可信度修正：

```text
final_score = rrf_score * trust_boost
```

trust_boost：

| trust_level | boost |
|---|---:|
| high | 1.00 |
| medium | 0.92 |
| low | 0.80 |

注意：

```text
trust_boost 只做轻微修正
不能让可信等级完全压倒相关性
```

---

### 9.3 如果接入 Rerank

如果启用 Rerank：

```text
Milvus + OpenSearch
  ↓
RRF topN = 30
  ↓
Rerank
  ↓
final topK = 5
```

Rerank 后：

```text
final_score = 0.70 * rerank_score + 0.30 * normalized_rrf_score
```

同样不再使用 hard trust sort。

---

## 10. 检索日志改造

### 10.1 retrieval_log 字段

建议最终字段：

```sql
ALTER TABLE retrieval_log ADD COLUMN vector_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN vector_rank INT;
ALTER TABLE retrieval_log ADD COLUMN keyword_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN keyword_rank INT;
ALTER TABLE retrieval_log ADD COLUMN rrf_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN rerank_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN trust_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN final_score DECIMAL(8,6);
ALTER TABLE retrieval_log ADD COLUMN search_type VARCHAR(32);
```

### 10.2 search_type 枚举

```text
VECTOR_ONLY
KEYWORD_ONLY
HYBRID_RRF
HYBRID_RRF_RERANK
```

### 10.3 日志记录规则

| 情况 | 记录 |
|---|---|
| 只被 Milvus 召回 | vector_score、vector_rank、rrf_score |
| 只被 OpenSearch 召回 | keyword_score、keyword_rank、rrf_score |
| 两边都召回 | vector_score、keyword_score、vector_rank、keyword_rank、rrf_score |
| 进入 Rerank | rerank_score |
| 进入 Prompt | used_in_prompt = true |

---

## 11. 索引同步设计

### 11.1 写入知识时同步两套索引

知识入库流程：

```text
knowledge_item 写入关系库
  ↓
Embedding
  ↓
写入 Milvus
  ↓
写入 OpenSearch
  ↓
status = enabled
```

如果 Milvus 或 OpenSearch 任意一个失败：

```text
status = index_failed
记录 index_failed_reason
进入补偿任务
```

---

### 11.2 审核通过时同步索引

低可信 / 中可信审核为高可信时：

```text
更新关系库 trust_level = high
  ↓
更新 Milvus metadata
  ↓
更新 OpenSearch document
```

注意：

```text
Milvus metadata 和 OpenSearch document 必须保持一致
```

---

### 11.3 删除 / 拒绝 / 过期时同步索引

处理方式：

```text
关系库 status = rejected / expired
Milvus status = rejected / expired 或删除向量
OpenSearch status = rejected / expired 或删除文档
```

第一版建议：

```text
逻辑删除优先，即更新 status，不直接物理删除
```

原因：

```text
方便审计
方便回滚
避免误删
```

---

### 11.4 index_sync_task 表

```sql
CREATE TABLE index_sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    target_index VARCHAR(32) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    finished_at DATETIME
);
```

target_index：

```text
MILVUS
OPENSEARCH
BOTH
```

operation：

```text
UPSERT
DELETE
UPDATE_STATUS
REBUILD
```

status：

```text
PENDING
RUNNING
SUCCESS
FAILED
```

---

## 12. 核心接口设计

### 12.1 VectorSearchClient

```java
public interface VectorSearchClient {

    VectorSearchResult search(VectorSearchRequest request);

    void upsert(VectorIndexDocument document);

    void updateStatus(Long knowledgeId, String status);
}
```

---

### 12.2 KeywordSearchClient

```java
public interface KeywordSearchClient {

    KeywordSearchResult search(KeywordSearchRequest request);

    void upsert(KeywordIndexDocument document);

    void updateStatus(Long knowledgeId, String status);
}
```

---

### 12.3 HybridRetriever

```java
public interface HybridRetriever {

    RetrievalResult retrieve(RagRequest request,
                             List<String> rewrittenQueries,
                             float[] queryEmbedding);
}
```

---

### 12.4 RrfFusionService

```java
public interface RrfFusionService {

    List<HybridCandidate> fuse(VectorSearchResult vectorResult,
                               KeywordSearchResult keywordResult);
}
```

---

### 12.5 IndexSyncService

```java
public interface IndexSyncService {

    void upsertToAllIndexes(Long knowledgeId);

    void updateStatus(Long knowledgeId, String status);

    void rebuild(Long knowledgeId);
}
```

---

## 13. 配置设计

固定配置如下：

```yaml
trust-rag:
  retrieval:
    mode: hybrid-rrf
    prompt-top-k: 5
    fusion-top-n: 30

  milvus:
    enabled: true
    host: localhost
    port: 19530
    collection: trust_rag_knowledge_vector
    dimension: 1536
    metric-type: COSINE
    vector-top-k: 30

  opensearch:
    enabled: true
    uris:
      - http://localhost:9200
    username: admin
    password: admin
    index-name: trust_rag_knowledge_keyword
    keyword-top-k: 30
    auto-create-index: true

  rrf:
    enabled: true
    k: 60

  trust-boost:
    high: 1.00
    medium: 0.92
    low: 0.80

  rerank:
    enabled: false
    top-n: 30
    top-k: 5

  retrieval-log:
    enabled: true
    save-vector-score: true
    save-keyword-score: true
    save-rrf-score: true
```

---

## 14. Engine 主流程改造

当前流程：

```text
Embedding
  ↓
Milvus Vector Search
  ↓
权限与可信等级过滤
  ↓
可选 Rerank
```

改造后：

```text
Query Rewrite
  ↓
Embedding
  ↓
并行执行：
    Milvus Vector Search
    OpenSearch BM25 Search
  ↓
RRF Fusion
  ↓
Metadata Enrich
  ↓
Trust Boost
  ↓
可选 Rerank
  ↓
Prompt TopK
  ↓
LLM Answer
  ↓
Retrieval Log
```

### 14.1 并行检索

建议使用：

```java
CompletableFuture
```

伪代码：

```java
CompletableFuture<VectorSearchResult> vectorFuture = CompletableFuture.supplyAsync(
    () -> vectorSearchClient.search(vectorRequest), executor
);

CompletableFuture<KeywordSearchResult> keywordFuture = CompletableFuture.supplyAsync(
    () -> keywordSearchClient.search(keywordRequest), executor
);

VectorSearchResult vectorResult = vectorFuture.get();
KeywordSearchResult keywordResult = keywordFuture.get();

List<HybridCandidate> candidates = rrfFusionService.fuse(vectorResult, keywordResult);
```

### 14.2 降级策略

如果 OpenSearch 失败：

```text
只使用 Milvus vector result
search_type = VECTOR_ONLY
记录 warning
不影响主问答
```

如果 Milvus 失败：

```text
只使用 OpenSearch keyword result
search_type = KEYWORD_ONLY
记录 warning
不影响主问答
```

如果两者都失败：

```text
返回无上下文
触发知识缺口检测
```

---

## 15. 开发步骤

### 第 1 步：新增 OpenSearch 模块

任务：

```text
1. 新建 trust-rag-opensearch 模块
2. 引入 OpenSearch Java Client
3. 编写 OpenSearchProperties
4. 编写 OpenSearchIndexInitializer
5. 启动时自动创建索引
```

验收：

```text
应用启动后 OpenSearch 自动存在 trust_rag_knowledge_keyword 索引
```

---

### 第 2 步：实现关键词索引同步

任务：

```text
1. 定义 KeywordIndexDocument
2. knowledge_item 入库时构造 KeywordIndexDocument
3. 写入 OpenSearch
4. 审核通过时更新 OpenSearch
5. rejected / expired 时更新 OpenSearch status
```

验收：

```text
知识入库后可以在 OpenSearch 中查到对应文档
```

---

### 第 3 步：实现 BM25 查询

任务：

```text
1. 实现 OpenSearchKeywordSearchClient
2. multi_match 查询 title^3 / claim^2 / tags^2 / content
3. 加入权限 filter
4. 返回 keyword_score 和 keyword_rank
```

验收：

```text
输入类名、错误码、接口路径可以命中关键词结果
```

---

### 第 4 步：实现 RRF 融合

任务：

```text
1. 定义 HybridCandidate
2. 实现 RrfFusionService
3. 记录 vector_rank
4. 记录 keyword_rank
5. 计算 rrf_score
6. 去重同一 knowledge_id
```

验收：

```text
同时被向量和关键词命中的知识排名提升
```

---

### 第 5 步：改造 TrustAwareRetriever

任务：

```text
1. 原 Milvus 单路检索改为 HybridRetriever
2. 并行调用 Milvus 和 OpenSearch
3. RRF 融合
4. trust_boost 修正
5. 可选 Rerank
6. 返回最终 RetrievedChunk
```

验收：

```text
TrustRagEngine.ask() 默认走 hybrid-rrf
```

---

### 第 6 步：改造 retrieval_log

任务：

```text
1. 新增字段
2. 记录 vector_score
3. 记录 keyword_score
4. 记录 vector_rank
5. 记录 keyword_rank
6. 记录 rrf_score
7. 记录 search_type
```

验收：

```text
keyword_score 不再是 null
search_type 可以是 HYBRID_RRF
```

---

### 第 7 步：补偿任务

任务：

```text
1. 新增 index_sync_task 表
2. OpenSearch 写入失败创建任务
3. Milvus 写入失败创建任务
4. 定时重试 FAILED 任务
5. 超过重试次数标记 index_failed
```

验收：

```text
检索索引失败不会导致数据永久不一致
```

---

### 第 8 步：测试

必须新增测试：

```text
1. OpenSearch 索引创建测试
2. BM25 查询测试
3. 权限过滤测试
4. Milvus + OpenSearch 双路召回测试
5. RRF 融合测试
6. search_type 日志测试
7. OpenSearch 失败降级测试
8. Milvus 失败降级测试
9. 索引同步失败补偿测试
```

---

## 16. 验收标准

完成后必须满足：

```text
1. 系统默认使用 OpenSearch + Milvus + RRF
2. retrieval_log.keyword_score 不再始终为 null
3. retrieval_log 能记录 vector_rank、keyword_rank、rrf_score
4. search_type 能记录 HYBRID_RRF
5. 专有名词、错误码、接口路径能通过 OpenSearch 命中
6. 语义相似问题能通过 Milvus 命中
7. 同时被两路召回的知识能通过 RRF 排名提升
8. 权限过滤在 Milvus 和 OpenSearch 两边都生效
9. OpenSearch 失败时可降级为 Vector Only
10. Milvus 失败时可降级为 Keyword Only
11. 索引同步失败有补偿任务
12. Prompt 最终引用知识来自 RRF 融合后的结果
```

---

## 17. 最终效果

改造前：

```text
用户问题
  ↓
Milvus 向量检索
  ↓
Prompt
  ↓
LLM
```

改造后：

```text
用户问题
  ↓
Query Rewrite
  ↓
Embedding
  ↓
Milvus 向量检索 + OpenSearch BM25 检索
  ↓
RRF 融合
  ↓
可信度轻量修正
  ↓
可选 Rerank
  ↓
Prompt
  ↓
LLM
  ↓
完整检索日志
```

最终系统将具备真正的 Hybrid Search 能力：

```text
Milvus 负责语义召回
OpenSearch 负责关键词召回
RRF 负责稳定融合
TrustRAG 负责权限、可信等级、Trace 和知识治理
```

