# TrustRAG 三池完整系统补充开发文档

> 当前阶段：Engine 模式 MVP 已完成  
> 目标阶段：完整三池可信自进化 RAG 系统  
> 核心升级方向：从「低可信候选 → 人工审核 → 高可信知识」升级为「低可信池 → 中可信池 → 高可信池」的完整知识治理闭环。

---

## 1. 当前系统状态

当前系统已经具备完整主链路：

```text
提问
  ↓
Query Rewrite
  ↓
Embedding
  ↓
Milvus 向量检索
  ↓
权限与可信等级过滤
  ↓
可选 Rerank
  ↓
Prompt 构造
  ↓
LLM 回答
  ↓
Trace 记录
  ↓
知识缺口检测
```

当前系统也已经具备基础知识闭环：

```text
用户纠错
  ↓
低可信候选
  ↓
人工审核
  ↓
重新向量化
  ↓
高可信知识
  ↓
后续召回
```

已实现能力包括：

```text
Spring AI 模型接口
Milvus 向量库
PostgreSQL / MySQL 元数据存储
文档切块与导入
多租户 / 项目 / 用户 / 会话隔离
高低可信知识分层
Prompt 防注入约束
Trace 和检索日志
规则型知识缺口检测
反馈与人工审核 API
Spring Boot Starter 自动装配
可运行示例和自动化测试
```

当前系统已经是一个可运行的 Engine 模式 RAG 框架。下一步重点不应继续扩普通 RAG 主链路，而应补齐完整的三池知识治理系统。

---

## 2. 完整三池系统目标

完整系统的核心链路应为：

```text
低可信池
  ↓
规则过滤
  ↓
重复检测
  ↓
隐私过滤
  ↓
LLM 预审
  ↓
来源评分
  ↓
冲突检测
  ↓
晋升评分
  ↓
中可信池
  ↓
人工审核
  ↓
高可信池
```

也就是把当前的：

```text
低可信候选
  ↓
人工审核
  ↓
高可信知识
```

升级为：

```text
低可信池
  ↓
自动预审与晋升
  ↓
中可信池
  ↓
人工终审
  ↓
高可信池
```

---

## 3. 三池定义

### 3.1 低可信池 low trust pool

低可信池用于存放系统在真实使用中沉淀出的临时候选知识。

来源包括：

```text
用户纠错
用户补充说明
知识缺口检测结果
工具查询结果
LLM 生成后的候选摘要
人工手动添加的待审核知识
```

特点：

```text
可信度最低
默认不跨用户 / 项目 / 租户
不能作为正式事实依据
主要用于候选知识沉淀
需要后续晋升评估
```

建议状态：

```text
low_pending
low_enabled
promotion_pending
promotion_running
```

---

### 3.2 中可信池 medium trust pool

中可信池用于存放已经通过自动预审，但尚未人工终审的候选知识。

来源：

```text
低可信池中通过规则过滤、重复检测、隐私过滤、LLM 预审、来源评分和冲突检测的知识
```

特点：

```text
可信度中等
可以参与回答
检索权重低于高可信池
Prompt 中必须标记为候选知识
不能覆盖高可信知识
进入高可信池前必须人工审核
```

建议状态：

```text
medium_enabled
human_review_pending
```

---

### 3.3 高可信池 high trust pool

高可信池用于存放正式知识。

来源：

```text
人工导入文档
官方文档
企业内部可信文档
人工审核通过的中可信知识
管理员直接确认的知识
```

特点：

```text
可信度最高
检索权重最高
可以作为回答主要依据
需要可追溯来源和审核记录
```

建议状态：

```text
high_enabled
```

---

### 3.4 拒绝池 / 冲突池 / 过期池

完整系统不应只有三池，还需要异常状态池。

#### rejected

用于保存被拒绝的知识。

典型原因：

```text
事实错误
无来源证据
低价值
重复
包含敏感信息
人工审核拒绝
```

#### conflict

用于保存与高可信知识冲突的候选知识。

#### expired

用于保存过期知识。

#### index_failed

用于保存数据库更新成功但 Milvus 索引失败的知识。

---

## 4. 下一阶段新增核心模块

---

## 4.1 Knowledge Promotion Engine：知识晋升引擎

### 4.1.1 模块职责

Knowledge Promotion Engine 是三池系统的核心模块。

职责：

```text
判断低可信知识是否可以晋升到中可信池
判断候选知识是否应保留、拒绝、标记冲突或过期
计算 promotion_score
驱动 low → medium 的状态流转
```

### 4.1.2 输入数据

```text
低可信知识内容
来源类型
来源证据
用户反馈
使用次数
LLM 预审结果
重复检测结果
冲突检测结果
隐私检测结果
时效检测结果
```

### 4.1.3 输出结果

```text
promote_to_medium
keep_low
reject
mark_conflict
mark_expired
merge_pending
```

### 4.1.4 接口设计

```java
public interface KnowledgePromotionEngine {

    PromotionResult evaluate(Long knowledgeId);

    void promoteToMedium(Long knowledgeId, PromotionResult result);

    void reject(Long knowledgeId, String reason);

    void markConflict(Long knowledgeId, String reason);

    void keepLow(Long knowledgeId, String reason);
}
```

### 4.1.5 PromotionResult

```java
public class PromotionResult {

    private Long knowledgeId;

    private boolean promotable;

    private String targetTrustLevel;

    private String targetStatus;

    private double promotionScore;

    private double llmScore;

    private double sourceScore;

    private double evidenceScore;

    private double feedbackScore;

    private double usageScore;

    private double conflictRisk;

    private double privacyRisk;

    private double staleRisk;

    private String reason;
}
```

---

## 4.2 LLM PreReview：LLM 预审模块

### 4.2.1 模块职责

LLM 预审模块负责对低可信知识进行自动初筛。

它可以做：

```text
质量评分
知识断言规范化
标题生成
标签生成
判断是否具备通用价值
判断表达是否清晰
判断证据是否充分
判断是否存在明显风险
给出晋升建议
```

它不能做：

```text
最终事实确认
直接晋升高可信池
替代人工审核
```

### 4.2.2 预审输入

```text
候选知识 title
候选知识 claim
候选知识 content
source_type
evidence
scope_type
当前相似知识
```

### 4.2.3 预审输出 JSON

```json
{
  "quality_score": 0.82,
  "general_value_score": 0.75,
  "evidence_sufficiency_score": 0.64,
  "risk_score": 0.21,
  "suggest_action": "promote_to_medium",
  "reason": "该知识表达清晰，具备通用技术价值，但来源仍需人工确认。",
  "normalized_claim": "Spring AI 支持通过 ToolCallback 动态注册工具。",
  "tags": ["Spring AI", "ToolCallback", "Agent"]
}
```

### 4.2.4 注意事项

```text
LLM 预审必须异步执行
不要放在用户问答主链路中
LLM 输出必须结构化
LLM 结果必须保存原始 JSON
LLM 评分只作为 promotion_score 的一部分
```

---

## 4.3 Duplicate Detector：重复检测模块

### 4.3.1 模块职责

防止低可信池和中可信池膨胀。

### 4.3.2 检测方式

#### 第一层：claim hash 去重

```text
normalized_claim
  ↓
去空格
  ↓
去标点
  ↓
统一大小写
  ↓
hash
```

适合检测完全重复知识。

#### 第二层：向量相似去重

```text
候选知识 embedding
  ↓
Milvus 检索相似知识
  ↓
similarity > 0.92
  ↓
认为高度相似
```

### 4.3.3 处理策略

| 情况 | 处理 |
|---|---|
| 完全重复 | 不新增，usage_count + 1 |
| 高度相似 | 进入 merge_pending 或合并 evidence |
| 表达不同但语义相近 | 进入人工合并审核 |
| 不重复 | 继续晋升流程 |

### 4.3.4 建议接口

```java
public interface DuplicateDetector {

    DuplicateCheckResult check(KnowledgeItem candidate);
}
```

---

## 4.4 Conflict Detector：冲突检测模块

### 4.4.1 模块职责

判断候选知识是否与已有高可信 / 中可信知识冲突。

### 4.4.2 检测流程

```text
候选知识
  ↓
Embedding
  ↓
检索相似 high 知识 topK
  ↓
检索相似 medium 知识 topK
  ↓
LLM 判断关系
      support
      conflict
      unrelated
      version_diff
  ↓
写入 conflict_record
```

### 4.4.3 冲突关系

| 类型 | 含义 |
|---|---|
| support | 新知识支持已有知识 |
| conflict | 新知识与已有知识冲突 |
| unrelated | 无关 |
| version_diff | 可能是版本差异，不一定是真冲突 |

### 4.4.4 注意版本问题

以下情况不能简单判断为冲突：

```text
Spring AI 0.8 不支持某功能
Spring AI 1.1 支持某功能
```

所以知识需要增加：

```text
applicable_version
valid_from
valid_to
source_time
```

---

## 4.5 Evidence Verifier：来源证据校验模块

### 4.5.1 模块职责

判断候选知识是否有可靠来源。

### 4.5.2 source_type 默认评分

| source_type | source_score |
|---|---:|
| official_doc | 0.95 |
| internal_doc | 0.90 |
| database_result | 0.85 |
| tool_result | 0.80 |
| user_correction | 0.60 |
| llm_summary | 0.30 |
| unknown | 0.10 |

### 4.5.3 基础规则

```text
没有 evidence 的知识不能自动进入 medium
source_score 太低不能进入 medium
LLM 自己总结的知识默认不能自动晋升
用户纠错可以进入 low，但进入 medium 需要额外证据或人工确认
```

---

## 4.6 Lifecycle Manager：生命周期管理模块

### 4.6.1 模块职责

管理知识从创建到废弃的完整生命周期。

### 4.6.2 生命周期能力

```text
过期
降权
回滚
拒绝池
版本管理
相似知识合并
负反馈降级
索引失败补偿
```

### 4.6.3 优先级

建议按以下顺序实现：

```text
rejected
  ↓
expired
  ↓
rollback
  ↓
merge
  ↓
auto downgrade
```

---

## 5. 知识状态机设计

### 5.1 推荐状态

```text
low_pending
low_enabled
promotion_pending
promotion_running
medium_enabled
human_review_pending
high_enabled
rejected
conflict
expired
index_failed
merge_pending
rollback
```

### 5.2 正常状态流转

```text
low_pending
  ↓
low_enabled
  ↓
promotion_pending
  ↓
promotion_running
  ↓
medium_enabled
  ↓
human_review_pending
  ↓
high_enabled
```

### 5.3 异常状态流转

```text
promotion_running → rejected
promotion_running → conflict
promotion_running → low_enabled
promotion_running → index_failed
medium_enabled → rejected
medium_enabled → low_enabled
high_enabled → expired
high_enabled → rollback
```

### 5.4 状态说明

| 状态 | 说明 |
|---|---|
| low_pending | 低可信知识刚生成，尚未启用 |
| low_enabled | 低可信知识可在限定作用域内召回 |
| promotion_pending | 等待晋升任务处理 |
| promotion_running | 正在执行晋升评估 |
| medium_enabled | 已进入中可信池 |
| human_review_pending | 等待人工审核进入高可信池 |
| high_enabled | 高可信正式知识 |
| rejected | 已拒绝 |
| conflict | 存在冲突 |
| expired | 已过期 |
| index_failed | 向量索引失败 |
| merge_pending | 等待合并 |
| rollback | 已回滚 |

---

## 6. 数据库补充设计

---

## 6.1 knowledge_item 字段补充

建议补充以下字段：

```sql
ALTER TABLE knowledge_item ADD COLUMN promotion_stage VARCHAR(32);
ALTER TABLE knowledge_item ADD COLUMN promotion_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN llm_pre_review_result TEXT;
ALTER TABLE knowledge_item ADD COLUMN normalized_claim TEXT;
ALTER TABLE knowledge_item ADD COLUMN claim_hash VARCHAR(128);
ALTER TABLE knowledge_item ADD COLUMN source_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN evidence_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN general_value_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN privacy_risk DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN conflict_risk DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN stale_risk DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN applicable_version VARCHAR(128);
ALTER TABLE knowledge_item ADD COLUMN valid_from DATETIME;
ALTER TABLE knowledge_item ADD COLUMN valid_to DATETIME;
ALTER TABLE knowledge_item ADD COLUMN last_verified_at DATETIME;
```

### 字段说明

| 字段 | 作用 |
|---|---|
| promotion_stage | 当前晋升阶段 |
| promotion_score | 晋升总分 |
| llm_pre_review_result | LLM 预审原始 JSON |
| normalized_claim | 标准化知识断言 |
| claim_hash | 去重 |
| source_score | 来源可信度 |
| evidence_score | 证据充分度 |
| general_value_score | 通用价值分 |
| privacy_risk | 隐私风险 |
| conflict_risk | 冲突风险 |
| stale_risk | 过期风险 |
| applicable_version | 适用版本 |
| valid_from / valid_to | 有效期 |
| last_verified_at | 最近验证时间 |

---

## 6.2 promotion_task 表

```sql
CREATE TABLE promotion_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    task_type VARCHAR(32),
    retry_count INT DEFAULT 0,
    error_message TEXT,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 状态

```text
pending
running
success
failed
skipped
```

### task_type

```text
low_to_medium
recheck_conflict
recheck_expired
merge_check
```

---

## 6.3 conflict_record 表

```sql
CREATE TABLE conflict_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    candidate_knowledge_id BIGINT NOT NULL,
    existing_knowledge_id BIGINT NOT NULL,
    conflict_type VARCHAR(32),
    confidence DECIMAL(5,4),
    reason TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 6.4 knowledge_lineage 表

```sql
CREATE TABLE knowledge_lineage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    parent_knowledge_id BIGINT,
    source_trace_id VARCHAR(64),
    source_feedback_id BIGINT,
    action VARCHAR(64),
    operator_type VARCHAR(32),
    operator_id VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 作用

用于追踪：

```text
知识从哪次对话产生
由谁审核
是否经过 LLM 预审
是否被修改
是否从 low 晋升到 medium
是否从 medium 晋升到 high
是否被拒绝或回滚
```

---

## 7. 三池检索逻辑升级

### 7.1 分层召回策略

完整三池系统中，检索应该变成：

```text
高可信池 topK = 5
中可信池 topK = 3
当前会话低可信池 topK = 2
当前用户低可信池 topK = 2
当前项目低可信池 topK = 2
当前租户低可信池 topK = 2
公共低可信候选 topK = 1
```

### 7.2 权重建议

| 池子 | 权重 |
|---|---:|
| high | 1.00 |
| medium | 0.70 |
| low_conversation | 0.60 |
| low_user | 0.45 |
| low_project | 0.45 |
| low_tenant | 0.35 |
| low_global_candidate | 0.15 |

### 7.3 Prompt 约束升级

Prompt 中必须明确：

```text
你会收到不同可信等级的知识：

high：
高可信知识，可作为主要依据。

medium：
候选知识，已通过自动预审，但未人工终审，只能作为辅助参考。

low：
低可信知识，仅用于当前会话或当前作用域，不可作为确定事实。

当 high 与 medium / low 冲突时，必须优先采用 high。
当没有 high 支撑时，需要说明不确定性。
```

---

## 8. 晋升评分设计

### 8.1 推荐公式

```text
promotion_score =
    0.25 * llm_score
  + 0.25 * source_score
  + 0.20 * feedback_score
  + 0.15 * usage_score
  + 0.10 * evidence_score
  - 0.20 * conflict_risk
  - 0.20 * privacy_risk
  - 0.10 * stale_risk
```

### 8.2 晋升条件

```text
promotion_score >= 0.75
AND source_score >= 0.60
AND evidence_score >= 0.50
AND privacy_risk <= 0.30
AND conflict_risk <= 0.30
```

满足条件：

```text
trust_level = medium
status = medium_enabled
```

不满足条件：

```text
继续 low
或者 rejected
或者 conflict
或者 expired
```

### 8.3 降级条件

```text
negative_feedback_count >= 3
OR conflict_risk >= 0.80
OR privacy_risk >= 0.80
OR valid_to < now()
```

处理方式：

```text
medium → low
medium → rejected
high → expired
high → rollback
```

---

## 9. 需要新增的 API

### 9.1 手动触发晋升任务

```http
POST /trust-rag/admin/promotion/run
```

作用：

```text
手动扫描 low_enabled 知识，创建或执行晋升任务
```

### 9.2 晋升任务列表

```http
GET /trust-rag/admin/promotion/tasks
```

查询参数：

```text
status
taskType
knowledgeId
```

### 9.3 中可信知识列表

```http
GET /trust-rag/admin/knowledge?trustLevel=medium
```

### 9.4 冲突记录列表

```http
GET /trust-rag/admin/conflicts
```

### 9.5 medium → high 审核

```http
POST /trust-rag/admin/knowledge/{id}/approve-high
```

### 9.6 知识降级

```http
POST /trust-rag/admin/knowledge/{id}/downgrade
```

示例：

```text
high → medium
medium → low
```

### 9.7 知识回滚

```http
POST /trust-rag/admin/knowledge/{id}/rollback
```

### 9.8 知识合并

```http
POST /trust-rag/admin/knowledge/merge
```

请求示例：

```json
{
  "sourceKnowledgeIds": [101, 102],
  "targetKnowledgeId": 201,
  "operatorId": "admin"
}
```

---

## 10. 需要新增的配置项

```yaml
trust-rag:
  promotion:
    enabled: true
    schedule: "0 0 3 * * ?"
    batch-size: 100
    min-promotion-score: 0.75
    min-source-score: 0.60
    min-evidence-score: 0.50
    max-conflict-risk: 0.30
    max-privacy-risk: 0.30
    llm-pre-review-enabled: true

  medium-pool:
    enabled: true
    retrieval-weight: 0.70
    top-k: 3
    allow-answer-reference: true
    require-high-priority-on-conflict: true

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
    llm-judge-enabled: true

  source-score:
    official-doc: 0.95
    internal-doc: 0.90
    database-result: 0.85
    tool-result: 0.80
    user-correction: 0.60
    llm-summary: 0.30
    unknown: 0.10

  lifecycle:
    low-ttl-days: 30
    medium-ttl-days: 90
    auto-expire-enabled: true
    negative-feedback-downgrade-threshold: 3
    index-failed-retry-limit: 3
```

---

## 11. 开发顺序

### 第 1 步：重构知识状态机

目标：

```text
让知识从 low 到 medium 到 high 的状态流转清晰可控
```

任务：

```text
1. 增加新的 status 枚举
2. 增加 promotion_stage
3. 修改原有审核逻辑
4. 保留 low → high 快速通道，但默认不用
5. 所有状态变更写 knowledge_lineage
```

交付物：

```text
完整知识状态机
```

---

### 第 2 步：新增中可信池检索

目标：

```text
让 medium 知识可以参与召回，但权重低于 high
```

任务：

```text
1. 修改 TrustAwareRetriever
2. 修改 Milvus filter
3. 修改 score fusion
4. 修改 PromptBuilder
5. 修改 RetrievalLog
6. 增加 medium-pool 配置
```

交付物：

```text
high / medium / low 三池召回
```

---

### 第 3 步：新增 promotion_task

目标：

```text
为 low → medium 晋升提供任务机制
```

任务：

```text
1. 创建 promotion_task 表
2. 编写 PromotionTaskService
3. 编写 PromotionWorker
4. 使用 Spring Scheduling 定期扫描 low_enabled
5. 支持手动触发
6. 支持失败重试
```

交付物：

```text
可异步执行的晋升任务
```

---

### 第 4 步：实现重复检测

目标：

```text
降低低可信池和中可信池膨胀
```

任务：

```text
1. 实现 normalized_claim
2. 实现 claim_hash
3. 实现 hash 去重
4. 实现向量相似去重
5. 实现重复知识合并策略
6. 增加 merge_pending 状态
```

交付物：

```text
DuplicateDetector
```

---

### 第 5 步：实现 LLM 预审

目标：

```text
自动评估低可信知识质量
```

任务：

```text
1. 设计 LLM 预审 Prompt
2. 要求 JSON 输出
3. 解析 llm_score
4. 解析 normalized_claim
5. 解析 tags
6. 保存 llm_pre_review_result
7. 失败可重试
```

交付物：

```text
LlmPreReviewService
```

---

### 第 6 步：实现来源评分

目标：

```text
让不同来源的知识具有不同可信度
```

任务：

```text
1. 按 source_type 配置 source_score
2. 检查 evidence 是否存在
3. 计算 evidence_score
4. 写入 knowledge_item
5. source_score 过低时禁止晋升
```

交付物：

```text
EvidenceVerifier
```

---

### 第 7 步：实现冲突检测

目标：

```text
防止与高可信知识冲突的内容进入中可信池
```

任务：

```text
1. 候选知识检索相似 high topK
2. 候选知识检索相似 medium topK
3. LLM 判断 support / conflict / unrelated / version_diff
4. 写 conflict_record
5. conflict_risk 高时禁止晋升
```

交付物：

```text
ConflictDetector
```

---

### 第 8 步：实现 low → medium 晋升

目标：

```text
跑通完整自动晋升链路
```

任务：

```text
1. 汇总 llm_score
2. 汇总 source_score
3. 汇总 feedback_score
4. 汇总 usage_score
5. 汇总 evidence_score
6. 扣除 conflict_risk
7. 扣除 privacy_risk
8. 扣除 stale_risk
9. 计算 promotion_score
10. 根据阈值更新状态
```

交付物：

```text
low → medium 自动晋升能力
```

---

### 第 9 步：改造人工审核逻辑

目标：

```text
把人工审核的主对象从 low 改为 medium
```

任务：

```text
1. medium_enabled 自动生成 human_review_pending
2. 审核页展示 LLM 预审结果
3. 展示 promotion_score
4. 展示来源证据
5. 展示冲突检测结果
6. 展示相似知识
7. 支持 approve-high
8. 支持 reject
9. 支持 downgrade
10. 支持 merge
```

交付物：

```text
medium → high 人工审核
```

---

### 第 10 步：补生命周期管理

目标：

```text
让知识库长期可维护
```

任务：

```text
1. low TTL 清理
2. medium TTL 检查
3. 负反馈自动降级
4. 过期检查
5. index_failed 补偿重试
6. rollback
7. merge
8. lineage 追踪
```

交付物：

```text
LifecycleManager
```

---

## 12. 必须补充的测试

### 12.1 三池召回测试

场景：

```text
同一个问题同时命中 high、medium、low
```

期望：

```text
high 排在 medium 前
medium 排在 low 前
Prompt 中标记可信等级
```

---

### 12.2 low → medium 晋升测试

构造：

```text
source_score = 0.8
llm_score = 0.85
evidence_score = 0.7
conflict_risk = 0.1
privacy_risk = 0.1
```

期望：

```text
进入 medium
```

---

### 12.3 冲突阻断测试

场景：

```text
候选知识与 high 知识冲突
```

期望：

```text
不进入 medium
status = conflict
写入 conflict_record
```

---

### 12.4 隐私阻断测试

场景：

```text
候选知识包含 API Key / 数据库连接串 / Token
```

期望：

```text
不进入 medium
status = rejected 或 private_only
```

---

### 12.5 重复合并测试

场景：

```text
候选知识与已有知识相似度 > 0.92
```

期望：

```text
不新增知识
usage_count + 1
或者进入 merge_pending
```

---

### 12.6 medium → high 审核测试

场景：

```text
人工审核 medium 知识
```

期望：

```text
trust_level = high
status = high_enabled
Milvus metadata 同步更新
knowledge_lineage 写入记录
```

---

### 12.7 过期降级测试

场景：

```text
valid_to < now()
```

期望：

```text
status = expired
不参与正常召回
```

---

### 12.8 index_failed 补偿测试

场景：

```text
关系库写入成功，Milvus 写入失败
```

期望：

```text
status = index_failed
补偿任务重试
成功后 status = enabled
```

---

## 13. 实现后的完整链路

最终完整系统链路：

```text
用户提问
  ↓
RAG 主链路回答
  ↓
Trace 记录
  ↓
知识缺口检测
  ↓
用户纠错 / 系统候选知识生成
  ↓
低可信池
  ↓
promotion_task
  ↓
重复检测
  ↓
隐私过滤
  ↓
LLM 预审
  ↓
来源评分
  ↓
冲突检测
  ↓
晋升评分
  ↓
中可信池
  ↓
人工审核
  ↓
高可信池
  ↓
后续召回提升
```

---

## 14. 开发优先级总结

建议严格按以下顺序开发：

```text
1. 知识状态机重构
2. 中可信池检索
3. promotion_task 晋升任务
4. 重复检测
5. LLM 预审
6. 来源评分
7. 冲突检测
8. low → medium 晋升
9. medium → high 人工审核
10. 生命周期管理
11. 三池测试与评估指标
```

不要优先做：

```text
复杂前端页面
GraphRAG
自动联网补知识
多模型复杂评估
全自动 high 晋升
```

---

## 15. 最终目标

补充完成后，系统将从当前的 MVP：

```text
低可信候选
  ↓
人工审核
  ↓
高可信知识
```

升级为完整三池可信自进化系统：

```text
低可信池
  ↓
自动预审和晋升
  ↓
中可信池
  ↓
人工终审
  ↓
高可信池
```

此时系统才真正具备：

```text
可控自进化
知识飞轮
可信分层
权限隔离
知识治理
人工终审
生命周期管理
```

核心里程碑是：

```text
low → medium → high
```

只要这个链路稳定跑通，TrustRAG 就从一个可插拔 RAG Starter 升级为一个完整的可信自进化 RAG 框架。
