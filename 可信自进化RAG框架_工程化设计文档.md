# 可信自进化 RAG 框架工程化设计文档

> 项目名称：Trust-Aware Self-Evolving RAG / Knowledge Flywheel RAG  
> 项目定位：面向企业知识库、技术问答、智能客服、Agent 平台的可信自进化 RAG 底层框架  
> 核心目标：让 RAG 系统在真实使用过程中发现知识缺口、沉淀候选知识，并通过可信度分层、权限隔离、审核晋升和生命周期管理，使知识库在可控、安全、可审计的前提下持续变好。

---

## 1. 项目工程定位

本项目不是一个简单的“文档问答系统”，而是一个带有知识治理能力的 RAG 框架。

普通 RAG 系统通常只完成以下流程：

```text
文档上传
  ↓
Chunk 切分
  ↓
Embedding 向量化
  ↓
向量检索
  ↓
LLM 生成回答
```

该流程本质上是静态的。知识库是否完整、知识是否过期、回答失败后如何改进，都依赖人工维护。

本项目在普通 RAG 的基础上增加“知识飞轮”能力：

```text
用户真实提问
  ↓
RAG 检索与回答
  ↓
发现知识缺口或回答失败
  ↓
生成候选知识
  ↓
进入低可信池
  ↓
LLM 预审、来源校验、冲突检测、反馈评估
  ↓
进入中可信池
  ↓
人工终审
  ↓
进入高可信主知识库
  ↓
下次类似问题回答质量提升
```

因此，本项目的工程定位可以定义为：

> 一个具备知识缺口检测、候选知识沉淀、可信度分层、权限隔离、知识晋升、人工审核和生命周期治理能力的企业级自进化 RAG 框架。

---

## 2. 系统设计目标

### 2.1 功能目标

系统需要支持以下核心能力：

1. 支持普通 RAG 问答。
2. 支持文档上传、解析、切分、向量化和入库。
3. 支持 BM25 + Vector 的混合检索。
4. 支持 Rerank 重排。
5. 支持基于可信度和作用域的分层检索。
6. 支持记录每次问答的 Query、召回内容、模型回答和反馈。
7. 支持自动识别低召回、低置信度、用户负反馈等知识缺口信号。
8. 支持从用户纠错、工具结果、对话上下文中抽取候选知识。
9. 支持低可信池、中可信池、高可信池的知识分层。
10. 支持 conversation、user、project、tenant、global 等作用域隔离。
11. 支持隐私过滤、脱敏和敏感信息拦截。
12. 支持 LLM 预审，但不允许 LLM 直接决定正式入库。
13. 支持人工审核，将候选知识晋升为高可信知识。
14. 支持知识过期、降权、回滚、拒绝池和版本管理。
15. 支持评估系统是否真的“越用越好”。

### 2.2 非功能目标

系统还需要满足以下非功能要求：

| 类型 | 要求 |
|---|---|
| 安全性 | 私有知识默认不跨用户、跨租户泄露 |
| 可控性 | 新知识不能直接污染正式知识库 |
| 可审计性 | 每条知识的来源、状态、版本、审批记录可追踪 |
| 可扩展性 | 后续可接入更多模型、向量库、文档源、审批规则 |
| 可观测性 | 每次检索、回答、晋升、拒绝都应有日志和指标 |
| 可回滚性 | 错误知识可以降权、撤回或回滚到历史版本 |
| 可评估性 | 可以通过 Recall、正确率、污染率等指标衡量效果 |

---

## 3. 总体架构

### 3.1 总体架构图

```text
┌───────────────────────────────────────────────┐
│                    前端层                      │
│  用户问答页 / 文档管理页 / 候选知识审核页 / 指标看板 │
└───────────────────────────────────────────────┘
                        ↓
┌───────────────────────────────────────────────┐
│                   API 接入层                   │
│  Chat API / Document API / Review API / Admin API │
└───────────────────────────────────────────────┘
                        ↓
┌───────────────────────────────────────────────┐
│                  应用服务层                    │
│  RAG 编排服务 / 知识治理服务 / 审批服务 / 评估服务 │
└───────────────────────────────────────────────┘
                        ↓
┌───────────────────────────────────────────────┐
│                  核心领域层                    │
│  Trust-Aware Retriever                         │
│  Knowledge Gap Detector                        │
│  Knowledge Extractor                           │
│  Privacy Filter                                │
│  Scope Classifier                              │
│  Knowledge Promotion Engine                    │
│  Conflict Detector                             │
│  Lifecycle Manager                             │
└───────────────────────────────────────────────┘
                        ↓
┌───────────────────────────────────────────────┐
│                 基础设施层                     │
│ PostgreSQL / PGVector / Milvus / Redis / MQ / LLM │
└───────────────────────────────────────────────┘
```

### 3.2 推荐分层

工程上建议采用分层架构：

```text
controller
  ↓
application service
  ↓
domain service
  ↓
repository / infrastructure adapter
```

示例包结构：

```text
com.example.trustrag
├── api
│   ├── ChatController
│   ├── DocumentController
│   ├── KnowledgeReviewController
│   └── AdminController
├── application
│   ├── RagApplicationService
│   ├── KnowledgeIngestionService
│   ├── KnowledgeGovernanceService
│   ├── ReviewApplicationService
│   └── EvaluationApplicationService
├── domain
│   ├── retrieval
│   ├── generation
│   ├── gap
│   ├── extraction
│   ├── privacy
│   ├── promotion
│   ├── conflict
│   └── lifecycle
├── infrastructure
│   ├── llm
│   ├── embedding
│   ├── vectorstore
│   ├── search
│   ├── database
│   ├── mq
│   └── scheduler
└── common
    ├── enums
    ├── exception
    ├── config
    └── utils
```

---

## 4. 核心业务流程

### 4.1 普通 RAG 问答流程

```text
用户输入问题
  ↓
Query Rewrite
  ↓
权限过滤
  ↓
分层召回
  ├── 高可信池
  ├── 中可信池
  ├── 当前会话低可信池
  └── 当前用户/项目低可信池
  ↓
Hybrid Search
  ↓
Rerank
  ↓
构造 Prompt
  ↓
LLM 生成回答
  ↓
返回答案
  ↓
记录问答日志、召回日志、模型使用日志
```

### 4.2 知识缺口发现流程

```text
问答完成
  ↓
质量评估
  ├── TopK 召回分数是否过低
  ├── Rerank 分数是否过低
  ├── 是否没有可用上下文
  ├── LLM 是否表达不确定
  ├── 用户是否点踩
  ├── 用户是否纠错
  └── 是否多轮追问仍未解决
  ↓
判断是否触发知识缺口
  ↓
生成 gap_event
```

### 4.3 候选知识生成流程

```text
触发知识缺口
  ↓
收集证据
  ├── 用户原始问题
  ├── 模型回答
  ├── 检索结果
  ├── 用户纠错内容
  ├── 工具查询结果
  └── 外部文档来源
  ↓
Knowledge Extractor 抽取候选知识断言
  ↓
Privacy Filter 隐私过滤
  ↓
Scope Classifier 判断作用域
  ↓
去重与合并
  ↓
写入低可信池
```

### 4.4 知识晋升流程

```text
低可信池候选知识
  ↓
规则过滤
  ├── 内容为空过滤
  ├── 重复内容过滤
  ├── 敏感内容过滤
  ├── 低价值内容过滤
  └── 无证据内容过滤
  ↓
LLM 预审
  ├── 质量评分
  ├── 结构化整理
  ├── 分类
  ├── 冲突初筛
  └── 建议是否晋升
  ↓
来源验证
  ↓
用户反馈统计
  ↓
冲突检测
  ↓
计算 promotion_score
  ↓
达到阈值进入中可信池
  ↓
人工审核
  ↓
通过后进入高可信池
  ↓
Embedding 入正式索引
```

### 4.5 知识生命周期流程

```text
知识创建
  ↓
低可信
  ↓
中可信
  ↓
高可信
  ↓
持续监控
  ├── 使用次数
  ├── 正反馈
  ├── 负反馈
  ├── 冲突情况
  ├── 过期时间
  └── 版本变化
  ↓
状态变化
  ├── 降权
  ├── 过期
  ├── 回滚
  ├── 拒绝
  └── 删除/归档
```

---

## 5. 可信知识池设计

### 5.1 高可信池

高可信池是正式知识库。

特点：

| 项目 | 说明 |
|---|---|
| trust_level | high |
| 默认权重 | 1.00 |
| 主要来源 | 官方文档、企业文档、人工确认内容 |
| 使用方式 | 默认参与召回，作为主要事实依据 |
| 审批要求 | 必须经过人工确认或可信来源导入 |

适合存放：

```text
官方接口文档
企业制度
产品说明
标准 FAQ
经过验证的技术知识
人工整理的业务知识
```

### 5.2 中可信池

中可信池是候选知识池。

特点：

| 项目 | 说明 |
|---|---|
| trust_level | medium |
| 默认权重 | 0.60 ~ 0.75 |
| 主要来源 | 低可信池晋升、LLM 预审通过、来源证据较强的候选知识 |
| 使用方式 | 可参与回答，但必须提示模型谨慎使用 |
| 审批要求 | 通过预审，但未人工终审 |

使用限制：

1. 不得覆盖高可信池结论。
2. 与高可信池冲突时，优先采用高可信池。
3. 回答中最好标记为“候选知识”或“未最终确认知识”。
4. 如果是企业场景，默认只在同租户范围内使用。

### 5.3 低可信池

低可信池是临时知识池。

特点：

| 项目 | 说明 |
|---|---|
| trust_level | low |
| 默认权重 | 0.15 ~ 0.60 |
| 主要来源 | 当前会话、用户纠错、工具结果、LLM 总结 |
| 使用方式 | 默认仅当前作用域可见 |
| 审批要求 | 未审批，不能作为正式事实依据 |

低可信池必须绑定作用域：

```text
conversation
user
project
tenant
global_candidate
```

默认策略：

```text
conversation 级知识：只对当前会话可见
user 级知识：只对当前用户可见
project 级知识：只对当前项目可见
tenant 级知识：只对当前租户可见
global_candidate：脱敏后才允许参与公共候选流程
```

### 5.4 拒绝池

拒绝池不是独立的向量库，而是一种状态。

用途：

1. 保存被判定错误的知识。
2. 保存重复、敏感、无价值、过期的知识。
3. 防止系统反复生成同类错误知识。
4. 支持审计和回滚。

典型字段：

```text
status = rejected
reject_reason = factual_error / duplicate / sensitive / low_value / expired / conflict
```

---

## 6. 核心模块设计

## 6.1 文档接入模块 Document Ingestion

### 6.1.1 职责

负责将外部文档转换为可检索知识。

支持来源：

```text
PDF
Word
Markdown
HTML
TXT
企业 Wiki
数据库记录
接口返回内容
Git 仓库文档
```

### 6.1.2 处理流程

```text
上传文档
  ↓
解析文本
  ↓
清洗内容
  ↓
结构化标题层级
  ↓
Chunk 切分
  ↓
生成 Embedding
  ↓
写入知识库
  ↓
建立索引
```

### 6.1.3 工程要点

1. 不同文档类型使用不同解析器。
2. 保留文档标题、章节、页码、来源路径。
3. Chunk 不应只按固定长度切分，应优先按标题、段落、语义边界切分。
4. 每个 Chunk 必须保留 source_id，便于溯源。
5. 入库时默认进入高可信池或中可信池，取决于文档来源。

---

## 6.2 Chunk 切分模块 Chunking

### 6.2.1 职责

将长文档切分成适合检索和生成的知识片段。

### 6.2.2 策略

建议支持多种策略：

| 策略 | 说明 | 适用场景 |
|---|---|---|
| Fixed Chunk | 固定长度切分 | 简单文本 |
| Recursive Chunk | 按标题、段落、句子递归切分 | 通用文档 |
| Semantic Chunk | 按语义相似度切分 | 长文档、论文 |
| QA Chunk | 按问答对切分 | FAQ |
| Code Chunk | 按类、方法、函数切分 | 代码库 |

### 6.2.3 推荐默认参数

MVP 阶段可以采用：

```text
chunk_size = 500 ~ 800 中文字符
chunk_overlap = 80 ~ 150 中文字符
```

但不要写死，应做成可配置项。

---

## 6.3 Embedding 模块

### 6.3.1 职责

负责将知识内容转换为向量表示。

### 6.3.2 工程要求

1. 统一封装 EmbeddingProvider 接口。
2. 支持不同 Embedding 模型切换。
3. 记录 embedding_model、embedding_dimension、embedding_version。
4. 当模型升级时，需要支持重新向量化。
5. 向量生成失败要支持重试和死信队列。

### 6.3.3 接口示例

```java
public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    String modelName();
    int dimension();
}
```

---

## 6.4 Hybrid Search 检索模块

### 6.4.1 职责

同时使用关键词检索和向量检索，提升召回质量。

```text
BM25：适合精确关键词、专有名词、接口名、错误码
Vector：适合语义相似、表达不同但含义相近的问题
```

### 6.4.2 检索流程

```text
Query Rewrite
  ↓
BM25 Search
  ↓
Vector Search
  ↓
结果合并
  ↓
去重
  ↓
初步排序
```

### 6.4.3 推荐融合方式

可以使用 RRF：

```text
RRF_score = 1 / (k + rank_bm25) + 1 / (k + rank_vector)
```

MVP 阶段可以先用简单加权：

```text
hybrid_score = 0.4 * bm25_score + 0.6 * vector_score
```

---

## 6.5 Trust-Aware Retriever 可信检索模块

### 6.5.1 职责

根据知识的可信度、作用域、权限和权重进行分层检索。

### 6.5.2 核心原则

1. 先权限过滤，再检索。
2. 先分层召回，再融合排序。
3. 高可信知识优先。
4. 中低可信知识只能作为辅助。
5. 与高可信知识冲突时，低可信知识不得覆盖高可信知识。

### 6.5.3 分层召回策略

```text
高可信池 TopK = 5
中可信池 TopK = 3
当前会话低可信池 TopK = 3
当前用户低可信池 TopK = 2
当前项目低可信池 TopK = 2
公共候选低可信池 TopK = 1
```

### 6.5.4 排序公式

可以先使用加权模型：

```text
final_score =
    α * semantic_score
  + β * keyword_score
  + γ * rerank_score
  + δ * trust_score
  + ε * freshness_score
  + ζ * feedback_score
  + η * scope_score
```

MVP 阶段建议：

```text
final_score = 0.6 * rerank_score + 0.25 * trust_score + 0.15 * freshness_score
```

---

## 6.6 Rerank 重排模块

### 6.6.1 职责

对初步召回的候选知识重新排序，提高最终上下文质量。

### 6.6.2 可选方案

| 方案 | 说明 |
|---|---|
| BGE-Reranker | 本地可部署，中文效果较好 |
| Cohere Rerank | 云服务，效果好但有成本 |
| LLM Rerank | 灵活但成本高、延迟高 |
| Cross Encoder | 准确但吞吐较低 |

### 6.6.3 工程建议

1. 初召回 TopN 可以取 20 ~ 50。
2. Rerank 后取 TopK 进入 Prompt。
3. Rerank 结果需要记录日志，便于评估。
4. 对低可信知识可以额外降权。

---

## 6.7 Answer Generator 回答生成模块

### 6.7.1 职责

基于检索上下文生成最终回答。

### 6.7.2 Prompt 要求

Prompt 必须明确区分知识可信度：

```text
你会收到不同可信等级的知识：
1. high：高可信知识，可作为主要依据。
2. medium：候选知识，只能作为辅助参考。
3. low：低可信上下文，仅用于当前会话，不可作为确定事实。
当不同等级知识冲突时，必须优先采用 high。
如果 high 不足以回答，应说明不确定，并可参考 medium/low 给出谨慎判断。
```

### 6.7.3 输出要求

回答结果应包含：

```text
answer
used_chunks
confidence
need_clarification
possible_gap
```

---

## 6.8 问答日志模块 QA Logging

### 6.8.1 职责

记录系统每一次问答过程，为后续评估和知识飞轮提供数据。

### 6.8.2 需要记录的数据

```text
用户问题
改写后的 Query
召回的 Chunk
每个 Chunk 的分数
每个 Chunk 的 trust_level
最终使用的上下文
模型回答
模型置信度
是否触发知识缺口
用户反馈
耗时
Token 消耗
```

### 6.8.3 作用

1. 支持问题复盘。
2. 支持自动评估。
3. 支持知识缺口检测。
4. 支持成本分析。
5. 支持 Prompt 和检索策略调优。

---

## 6.9 Knowledge Gap Detector 知识缺口检测模块

### 6.9.1 职责

判断一次问答是否暴露了知识库缺口。

### 6.9.2 触发信号

| 信号 | 说明 |
|---|---|
| top_score 过低 | 向量召回最高分低 |
| rerank_score 过低 | 重排后仍无强相关内容 |
| no_context | 没有可用知识片段 |
| llm_uncertain | 模型表达不确定 |
| user_negative_feedback | 用户点踩 |
| user_correction | 用户明确纠错 |
| repeated_followup | 多轮追问仍未解决 |
| tool_used | 靠外部工具才补齐答案 |

### 6.9.3 注意事项

回答不好不一定是知识缺失，也可能是检索、排序、Prompt 或问题表达的问题。

因此建议增加失败原因分类：

```text
NO_KNOWLEDGE：知识库确实缺失
RETRIEVAL_FAIL：有知识但没召回
RERANK_FAIL：召回了但排序不好
GENERATION_FAIL：召回了但模型没用好
QUERY_AMBIGUOUS：用户问题不清晰
KNOWLEDGE_EXPIRED：知识过期
```

---

## 6.10 Knowledge Extractor 知识抽取模块

### 6.10.1 职责

从会话、用户纠错、工具查询结果、文档内容中抽取可沉淀的候选知识。

### 6.10.2 抽取原则

不要存整段聊天记录，也不要存整段模型回答。应该抽取“可验证的知识断言”。

错误做法：

```text
把模型完整回答直接存入知识库。
```

正确做法：

```json
{
  "claim": "Spring AI 支持通过 ToolCallback 动态注册工具。",
  "evidence": "官方文档链接或用户提供的项目代码片段",
  "source_type": "official_doc",
  "scope_type": "global_candidate",
  "confidence": 0.82
}
```

### 6.10.3 候选知识格式

```json
{
  "title": "Spring AI ToolCallback 动态注册",
  "claim": "Spring AI 支持通过 ToolCallback 动态注册工具。",
  "content": "Spring AI 中 ToolCallback 可用于将外部工具注册给模型调用。",
  "evidence": "...",
  "source_type": "conversation / tool_result / document / user_correction",
  "scope_type": "conversation / user / project / tenant / global_candidate",
  "confidence": 0.82,
  "tags": ["Spring AI", "ToolCallback", "Agent"]
}
```

---

## 6.11 Privacy Filter 隐私过滤模块

### 6.11.1 职责

防止私有知识进入公共候选流程，避免数据泄露。

### 6.11.2 检测对象

```text
手机号
邮箱
身份证号
账号
Token
API Key
数据库连接串
内网 IP
企业内部路径
项目代码片段
用户个人信息
业务敏感数据
```

### 6.11.3 处理方式

| 风险等级 | 处理方式 |
|---|---|
| 高风险 | 拒绝进入公共池，只允许当前作用域使用 |
| 中风险 | 脱敏后允许进入候选流程 |
| 低风险 | 标记风险等级，继续后续流程 |

---

## 6.12 Scope Classifier 作用域分类模块

### 6.12.1 职责

判断知识应该对谁可见。

### 6.12.2 Scope 类型

| Scope | 说明 |
|---|---|
| conversation | 当前会话 |
| user | 当前用户 |
| project | 当前项目 |
| tenant | 当前租户 |
| global_candidate | 公共候选知识 |
| global | 正式全局知识 |

### 6.12.3 判断示例

| 知识内容 | Scope |
|---|---|
| “用户这个项目使用 PGVector” | project |
| “用户偏好 Java 技术栈” | user |
| “这次对话中的临时日志错误” | conversation |
| “Spring AI 支持 Advisor 机制” | global_candidate |
| “公司内部审批流程” | tenant |

---

## 6.13 Knowledge Promotion 知识晋升模块

### 6.13.1 职责

判断低可信知识是否可以晋升到中可信池。

### 6.13.2 晋升评分

```text
promotion_score =
    0.25 * llm_score
  + 0.25 * source_score
  + 0.20 * feedback_score
  + 0.15 * usage_score
  - 0.10 * conflict_risk
  - 0.10 * privacy_risk
  - 0.05 * stale_risk
```

### 6.13.3 晋升规则示例

```text
promotion_score >= 0.75
AND privacy_risk < 0.3
AND conflict_risk < 0.4
AND source_score >= 0.6
```

满足以上条件才允许进入中可信池。

### 6.13.4 降级规则

```text
negative_feedback_count >= 3
OR conflict_risk >= 0.8
OR privacy_risk >= 0.8
OR expired_at < now()
```

触发后进入：

```text
rejected / expired / conflict / low_trust
```

---

## 6.14 Conflict Detector 冲突检测模块

### 6.14.1 职责

检测候选知识是否与高可信主知识库冲突。

### 6.14.2 检测方式

```text
候选知识向量检索相似高可信知识
  ↓
抽取双方 claim
  ↓
LLM 判断关系
  ├── support：支持
  ├── conflict：冲突
  ├── unrelated：无关
  └── version_diff：版本差异
```

### 6.14.3 重要字段

为避免误判，知识需要记录：

```text
适用版本
生效时间
过期时间
来源时间
适用产品
适用租户
```

---

## 6.15 Human Review 人工审核模块

### 6.15.1 职责

人工决定中可信知识是否进入高可信主库。

### 6.15.2 审核页面应展示

```text
候选知识标题
候选知识内容
来源证据
LLM 评分
来源评分
反馈评分
冲突检测结果
隐私风险
使用次数
历史相似知识
建议操作
```

### 6.15.3 审核操作

```text
通过
拒绝
修改后通过
合并到已有知识
标记过期
标记冲突
退回低可信池
```

---

## 6.16 Lifecycle Manager 生命周期管理模块

### 6.16.1 职责

管理知识从创建到废弃的完整生命周期。

### 6.16.2 状态机

```text
pending
  ↓
low_trust
  ↓
medium_trust
  ↓
high_trust
  ↓
expired / rejected / rollback / archived
```

### 6.16.3 定时任务

| 任务 | 频率 | 说明 |
|---|---|---|
| 低可信池清理 | 每天 | 删除或归档长期未使用候选知识 |
| LLM 预审任务 | 每周 | 批量预审低可信知识 |
| 过期检查 | 每天 | 检查 expires_at |
| 冲突重检 | 每周 | 对中可信和高可信知识重新检测 |
| 反馈降权 | 实时/每天 | 根据负反馈降低权重 |
| Embedding 重建 | 模型升级时 | 重新向量化 |

---

## 6.17 Evaluation 评估模块

### 6.17.1 职责

证明系统是否真的变好。

### 6.17.2 检索指标

| 指标 | 说明 |
|---|---|
| Recall@K | 正确知识是否出现在 TopK |
| Precision@K | TopK 中有多少是有用内容 |
| MRR | 正确答案排序是否靠前 |
| NDCG | 综合排序质量 |

### 6.17.3 生成指标

| 指标 | 说明 |
|---|---|
| Answer Correctness | 答案是否正确 |
| Faithfulness | 答案是否忠于检索上下文 |
| Context Relevance | 上下文是否相关 |
| Hallucination Rate | 幻觉率 |

### 6.17.4 飞轮指标

| 指标 | 说明 |
|---|---|
| Gap Detection Precision | 缺口检测命中率 |
| Candidate Approval Rate | 候选知识通过率 |
| Promotion Precision | 晋升知识有效率 |
| Knowledge Reuse Rate | 新增知识复用率 |
| Contamination Rate | 错误知识污染率 |
| Privacy Leakage Rate | 权限泄露率 |
| Before/After Accuracy | 入库前后准确率变化 |

---

## 7. 数据库设计

## 7.1 knowledge_item 知识表

```sql
CREATE TABLE knowledge_item (
    id BIGSERIAL PRIMARY KEY,

    title VARCHAR(255),
    content TEXT NOT NULL,
    claim TEXT,
    summary TEXT,

    knowledge_type VARCHAR(32),
    trust_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(32),

    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    project_id VARCHAR(64),
    tenant_id VARCHAR(64),

    source_type VARCHAR(64),
    source_ref TEXT,
    source_title VARCHAR(255),
    source_url TEXT,
    source_time TIMESTAMP,
    evidence TEXT,
    evidence_required BOOLEAN DEFAULT TRUE,

    embedding vector(1536),
    embedding_model VARCHAR(128),
    embedding_version VARCHAR(64),

    llm_score NUMERIC(5, 4),
    source_score NUMERIC(5, 4),
    feedback_score NUMERIC(5, 4),
    usage_score NUMERIC(5, 4),
    conflict_score NUMERIC(5, 4),
    privacy_score NUMERIC(5, 4),
    stale_score NUMERIC(5, 4),
    promotion_score NUMERIC(5, 4),

    usage_count INT DEFAULT 0,
    positive_feedback_count INT DEFAULT 0,
    negative_feedback_count INT DEFAULT 0,

    parent_id BIGINT,
    supersedes_id BIGINT,
    version INT DEFAULT 1,
    hash VARCHAR(128),

    approved_by VARCHAR(64),
    approved_at TIMESTAMP,
    reject_reason TEXT,

    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
```

---

## 7.2 qa_session 问答会话表

```sql
CREATE TABLE qa_session (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    project_id VARCHAR(64),
    tenant_id VARCHAR(64),
    question TEXT NOT NULL,
    rewritten_query TEXT,
    answer TEXT,
    confidence NUMERIC(5,4),
    possible_gap BOOLEAN DEFAULT FALSE,
    gap_reason VARCHAR(64),
    latency_ms INT,
    prompt_tokens INT,
    completion_tokens INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.3 retrieval_log 检索日志表

```sql
CREATE TABLE retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    qa_session_id BIGINT NOT NULL,
    knowledge_id BIGINT,
    query TEXT,
    search_type VARCHAR(32),
    semantic_score NUMERIC(8,6),
    keyword_score NUMERIC(8,6),
    rerank_score NUMERIC(8,6),
    trust_score NUMERIC(8,6),
    final_score NUMERIC(8,6),
    rank INT,
    used_in_prompt BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.4 feedback 反馈表

```sql
CREATE TABLE feedback (
    id BIGSERIAL PRIMARY KEY,
    qa_session_id BIGINT NOT NULL,
    user_id VARCHAR(64),
    feedback_type VARCHAR(32),
    feedback_content TEXT,
    corrected_answer TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.5 review_task 审核任务表

```sql
CREATE TABLE review_task (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reviewer_id VARCHAR(64),
    review_action VARCHAR(32),
    review_comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);
```

---

## 7.6 conflict_record 冲突记录表

```sql
CREATE TABLE conflict_record (
    id BIGSERIAL PRIMARY KEY,
    candidate_knowledge_id BIGINT NOT NULL,
    existing_knowledge_id BIGINT NOT NULL,
    conflict_type VARCHAR(32),
    conflict_reason TEXT,
    confidence NUMERIC(5,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 8. 接口设计

## 8.1 问答接口

```http
POST /api/chat
```

请求：

```json
{
  "conversationId": "c_001",
  "projectId": "p_001",
  "question": "Spring AI 的 ToolCallback 是什么？"
}
```

响应：

```json
{
  "answer": "...",
  "confidence": 0.82,
  "usedKnowledge": [
    {
      "id": 1001,
      "title": "Spring AI ToolCallback",
      "trustLevel": "high",
      "score": 0.91
    }
  ],
  "possibleGap": false
}
```

---

## 8.2 用户反馈接口

```http
POST /api/feedback
```

请求：

```json
{
  "qaSessionId": 10001,
  "feedbackType": "correction",
  "feedbackContent": "这里说错了，ToolCallback 可以动态注册。",
  "correctedAnswer": "Spring AI 可以通过 ToolCallback 动态注册工具。"
}
```

---

## 8.3 文档上传接口

```http
POST /api/documents/upload
```

功能：

```text
上传文档
解析文本
切分 Chunk
Embedding
入知识库
```

---

## 8.4 候选知识列表接口

```http
GET /api/knowledge/candidates?status=low_trust
```

返回低可信池或中可信池中的候选知识。

---

## 8.5 审核接口

```http
POST /api/knowledge/{id}/review
```

请求：

```json
{
  "action": "approve",
  "comment": "来源可靠，内容正确，可进入主知识库。"
}
```

操作：

```text
approve：进入高可信池
reject：进入拒绝池
modify_and_approve：修改后通过
merge：合并到已有知识
rollback：回滚
```

---

## 9. 技术选型

### 9.1 MVP 推荐技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Spring Boot |
| AI 编排 | Spring AI |
| 数据库 | PostgreSQL |
| 向量检索 | PGVector |
| 关键词检索 | PostgreSQL Full Text Search |
| 缓存 | Redis |
| 定时任务 | Spring Scheduling |
| 前端 | Vue3 或 React |
| 管理后台 | Element Plus / Ant Design |
| 模型 | OpenAI / Qwen / DeepSeek |
| Rerank | BGE-Reranker 或云端 Rerank |

### 9.2 为什么 MVP 推荐 PGVector

MVP 阶段不建议一开始使用 Milvus，原因是：

1. PGVector 可以和 metadata 放在同一个数据库。
2. 权限过滤、scope 过滤、状态过滤更容易。
3. SQL 查询、审核后台、统计报表更方便。
4. 部署复杂度低。
5. 后续数据量变大后再迁移 Milvus。

### 9.3 后期扩展方案

当数据量变大时，可以调整为：

```text
PostgreSQL：存 metadata、权限、状态、审核记录
Milvus：存向量和主键
Elasticsearch/OpenSearch：做 BM25 和全文检索
Redis：缓存热点 Query 和检索结果
Kafka/RabbitMQ：处理异步 Embedding、预审和重建任务
```

---

## 10. 权限与安全设计

### 10.1 权限过滤顺序

必须先过滤权限，再做召回：

```text
用户请求
  ↓
识别 user_id / project_id / tenant_id / conversation_id
  ↓
构造可见 Scope
  ↓
只检索可见知识
  ↓
返回候选 Chunk
```

不能先召回再过滤，否则可能导致日志、Prompt 或中间结果泄露。

### 10.2 可见范围规则

```text
global：所有用户可见
tenant：同租户可见
project：同项目可见
user：当前用户可见
conversation：当前会话可见
global_candidate：只在公共候选流程中可见，不默认给普通用户使用
```

### 10.3 安全底线

1. 低可信私有知识默认不跨用户。
2. 用户上传内容默认不进入 global。
3. 包含密钥、连接串、账号的数据不得进入公共池。
4. 中可信池不得覆盖高可信池。
5. 所有晋升和审核操作必须可审计。
6. 删除最好采用软删除或归档，避免审计链断裂。

---

## 11. 评估体系

### 11.1 离线评估

建立测试集：

```text
问题
标准答案
标准知识片段
所属领域
难度
是否依赖新增知识
```

评估：

```text
Recall@5
MRR
Answer Correctness
Faithfulness
Hallucination Rate
```

### 11.2 在线评估

记录线上真实数据：

```text
用户点赞率
点踩率
纠错率
追问率
平均响应时间
候选知识生成数量
候选知识通过率
新增知识复用率
```

### 11.3 飞轮有效性评估

关键是做 Before/After 对比：

```text
某类问题在新增知识入库前的正确率
  vs
新增知识入库后的正确率
```

如果新增知识入库后：

```text
Recall@K 提升
Answer Correctness 提升
用户负反馈下降
同类问题追问次数下降
```

才说明知识飞轮有效。

---

## 12. 开发流程步骤

## 阶段 1：项目初始化

目标：搭建基础工程骨架。

步骤：

1. 创建 Spring Boot 项目。
2. 接入 PostgreSQL。
3. 安装并配置 PGVector。
4. 设计基础表结构。
5. 接入 Spring AI。
6. 封装 LLM Provider。
7. 封装 Embedding Provider。
8. 搭建简单前端或 Swagger 接口调试环境。

交付物：

```text
基础后端项目
数据库连接
模型调用能力
Embedding 调用能力
```

---

## 阶段 2：基础 RAG 问答

目标：先完成普通 RAG。

步骤：

1. 实现文档上传接口。
2. 实现文档解析。
3. 实现 Chunk 切分。
4. 实现 Embedding 生成。
5. 将 Chunk 写入 knowledge_item。
6. 实现向量检索。
7. 实现基础 Prompt 构造。
8. 实现 Chat API。
9. 返回答案和引用知识片段。

交付物：

```text
可运行的普通 RAG 问答系统
```

---

## 阶段 3：检索增强

目标：提升召回质量。

步骤：

1. 增加 PostgreSQL Full Text Search 或 Elasticsearch。
2. 实现 BM25 检索。
3. 实现 Vector 检索。
4. 实现 Hybrid Search 结果融合。
5. 接入 Rerank。
6. 记录 retrieval_log。
7. 调整 TopK、权重和 Prompt。

交付物：

```text
支持 Hybrid Search + Rerank 的 RAG 系统
```

---

## 阶段 4：问答日志与反馈

目标：为知识飞轮准备数据基础。

步骤：

1. 创建 qa_session 表。
2. 每次问答记录 Query、Answer、Token、耗时。
3. 创建 retrieval_log 表。
4. 记录召回 Chunk、分数、排名。
5. 创建 feedback 表。
6. 实现点赞、点踩、纠错接口。
7. 前端增加反馈按钮。

交付物：

```text
可追踪的问答与反馈系统
```

---

## 阶段 5：低可信候选知识池

目标：实现最小自进化闭环。

步骤：

1. 增加 trust_level、status、scope_type 字段。
2. 实现 Knowledge Gap Detector 初版。
3. 支持低召回、点踩、纠错触发 gap_event。
4. 实现 Knowledge Extractor。
5. 从用户纠错或工具结果中抽取候选知识。
6. 写入 knowledge_item，trust_level = low。
7. 低可信知识只允许当前会话或当前用户检索。
8. 增加候选知识管理页面。

交付物：

```text
低可信候选知识池
```

---

## 阶段 6：人工审核入库

目标：完成最小知识飞轮。

步骤：

1. 创建 review_task 表。
2. 候选知识自动生成审核任务。
3. 审核页面展示候选知识、来源、上下文、分数。
4. 支持通过、拒绝、修改后通过。
5. 通过后 trust_level 改为 high。
6. 重新生成 Embedding。
7. 正式参与高可信池检索。
8. 验证同类问题是否能召回新增知识。

交付物：

```text
低可信池 → 人工审核 → 高可信池的闭环
```

这是第一个真正可演示的里程碑。

---

## 阶段 7：权限与 Scope 隔离

目标：确保私有知识不泄露。

步骤：

1. 完善 user_id、conversation_id、project_id、tenant_id。
2. 实现 Scope Classifier。
3. 实现检索前权限过滤。
4. conversation 知识只对当前会话可见。
5. user 知识只对当前用户可见。
6. project 知识只对当前项目可见。
7. tenant 知识只对当前租户可见。
8. global 知识全局可见。
9. 编写权限测试用例。

交付物：

```text
支持多作用域隔离的可信 RAG 检索
```

---

## 阶段 8：LLM 预审与中可信池

目标：减少人工审核压力。

步骤：

1. 实现定时预审任务。
2. 对低可信池候选知识做规则过滤。
3. 对候选知识做去重。
4. 调用 LLM 输出质量评分、分类、摘要、晋升建议。
5. 计算 promotion_score。
6. 满足阈值后进入 medium_trust。
7. 中可信池参与检索但降低权重。
8. Prompt 中明确标记中可信知识。

交付物：

```text
低可信池 → LLM 预审 → 中可信池 → 人工审核 → 高可信池
```

---

## 阶段 9：隐私过滤与冲突检测

目标：降低知识污染和数据泄露风险。

步骤：

1. 实现敏感信息规则检测。
2. 检测手机号、邮箱、Token、连接串、内网 IP 等。
3. 实现脱敏处理。
4. 实现 Conflict Detector。
5. 候选知识与高可信池相似知识对比。
6. 判断 support、conflict、unrelated、version_diff。
7. 冲突知识禁止自动晋升。
8. 生成 conflict_record。

交付物：

```text
隐私安全与知识冲突检测能力
```

---

## 阶段 10：生命周期管理

目标：让知识库长期可维护。

步骤：

1. 实现 expires_at。
2. 实现低可信池 TTL 清理。
3. 实现负反馈自动降权。
4. 实现 rejected 状态。
5. 实现拒绝池查询。
6. 实现版本管理。
7. 实现 rollback。
8. 实现相似知识合并。
9. 实现定期冲突重检。

交付物：

```text
完整知识生命周期治理能力
```

---

## 阶段 11：评估与优化

目标：证明系统确实越用越好。

步骤：

1. 构建测试问题集。
2. 标注标准答案和标准知识片段。
3. 实现 Recall@K 评估。
4. 实现 MRR 评估。
5. 评估 Faithfulness 和 Answer Correctness。
6. 统计候选知识通过率。
7. 统计新增知识复用率。
8. 做 Before/After 对比。
9. 形成系统评估报告。

交付物：

```text
RAG 效果评估报告
知识飞轮有效性报告
```

---

## 13. 推荐 MVP 范围

如果是第一版，不建议一次性做完整系统。

推荐 MVP 范围如下：

```text
1. 基础 RAG 问答
2. 问答日志
3. 用户点踩和纠错
4. 低召回触发知识缺口
5. 候选知识抽取
6. 低可信池
7. 人工审核
8. 审核通过后进入高可信池
9. 再次提问可以召回新增知识
```

第一版可以暂时不做：

```text
中可信池
LLM 批量预审
复杂冲突检测
复杂生命周期
多租户隔离
复杂评估平台
```

等 MVP 闭环跑通后，再逐步扩展。

---

## 14. 项目最终形态

完整版本的系统应该具备以下能力：

```text
能回答：普通 RAG 问答能力
能发现：识别知识缺口和回答失败
能沉淀：抽取候选知识
能隔离：按会话、用户、项目、租户控制可见范围
能预审：LLM 进行质量评分和整理
能审核：人工终审进入主库
能治理：过期、降权、回滚、拒绝、合并
能评估：证明系统是否真的变好
```

最终系统不是一个简单的问答机器人，而是一个：

> 面向企业知识库和 Agent 平台的可信知识飞轮基础设施。

---

## 15. 关键工程风险与应对

| 风险 | 表现 | 应对 |
|---|---|---|
| 知识污染 | 错误知识进入主库 | 低可信池隔离、人工审核、拒绝池 |
| 隐私泄露 | 私有知识被其他用户召回 | 先权限过滤，再检索；Scope 隔离 |
| 低可信池膨胀 | 候选知识越来越多 | TTL、去重、低价值淘汰、合并 |
| LLM 预审不稳定 | 错误内容被推荐晋升 | LLM 只做预审，结合来源和人工审核 |
| 检索效果差 | 有知识但召回不到 | Hybrid Search、Rerank、Query Rewrite |
| 成本过高 | LLM 审核和向量化成本大 | 规则过滤、批处理、缓存、异步任务 |
| 系统复杂 | 模块太多难落地 | 分阶段开发，先完成 MVP 闭环 |
| 评估困难 | 无法证明越用越好 | 建测试集，做 Before/After 评估 |

---

## 16. 工程结论

该项目具备较强的工程价值，但不适合以“大而全”的方式直接开发。

正确开发方式是：

```text
先做普通 RAG
再做问答日志和反馈
再做低可信候选池
再做人工审核入库
最后逐步增加 LLM 预审、中可信池、冲突检测和生命周期管理
```

最小闭环是：

```text
用户提问
  ↓
RAG 回答失败或用户纠错
  ↓
系统生成候选知识
  ↓
进入低可信池
  ↓
人工审核通过
  ↓
进入高可信主库
  ↓
下次类似问题召回成功
```

只要这个闭环跑通，项目就已经成立。后续再逐步增强可信度分层、自动预审、权限隔离和评估体系。

最终，该项目可以定义为：

> 一个基于可信度分层、作用域隔离和人机协同审核机制的可信自进化 RAG 工程框架。
