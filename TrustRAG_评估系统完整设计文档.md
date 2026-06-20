# TrustRAG 评估系统完整设计文档

> 模块名称：`trust-rag-evaluation`  
> 目标：为 TrustRAG 提供标准测试集管理、检索指标评估、生成质量评估、知识治理指标统计、Before/After 对比和评估报告能力。  
> 固定技术路线：Java/Spring Boot + Spring Batch + Spring AI LLM-as-Judge + PostgreSQL + Vue3/ECharts。  
> 设计原则：评估系统独立于主问答链路，评估任务异步执行，评估调用不得污染知识飞轮。

---

## 1. 评估系统要解决的问题

当前系统已经具备 RAG 主链路、Trace、检索日志、知识缺口检测和知识闭环，但缺少一套系统性评估机制。

评估模块要回答以下问题：

```text
1. 检索效果是否达标？
2. 混合检索、Rerank、Query Rewrite 是否真的带来提升？
3. LLM 回答是否正确、忠于上下文、是否存在幻觉？
4. 知识入库后，同类问题的效果是否变好？
5. 候选知识是否真的有价值？
6. 三池晋升机制是否带来污染风险？
7. 权限隔离是否存在泄漏？
```

因此，评估系统分为三类指标：

```text
检索评估指标
生成质量指标
知识治理指标
```

---

## 2. 固定技术选型

本模块固定使用以下技术，不再设计多套可选路线。

| 方向 | 技术选型 |
|---|---|
| 后端框架 | Spring Boot 3.5.x |
| Java 版本 | Java 17 编译目标，JDK 21 开发环境 |
| 批处理任务 | Spring Batch |
| 异步执行 | ThreadPoolTaskExecutor |
| LLM Judge | Spring AI |
| 指标计算 | Java 自研 |
| 数据库 | PostgreSQL 16 |
| 检索执行 | 调用现有 TrustRagEngine |
| 向量检索 | Milvus |
| 关键词检索 | OpenSearch BM25 |
| 混合检索 | RRF |
| 前端 | Vue3 + TypeScript + Element Plus |
| 图表 | ECharts |
| API 鉴权 | Spring Security + JWT |

### 2.1 为什么检索指标用 Java 自研

Recall@K、Precision@K、MRR、NDCG 都是确定性指标，不需要 LLM。

它们只依赖：

```text
标准知识 ID
实际召回知识 ID
排序位置
```

所以应直接用 Java 计算，保证稳定、便宜、可复现。

### 2.2 为什么生成质量用 Spring AI LLM-as-Judge

Faithfulness、Answer Correctness、Answer Relevance、幻觉率这类指标需要判断语义和事实一致性，适合使用 LLM-as-Judge。

但是 LLM Judge 的结果不是绝对真理，所以必须：

```text
1. 固定 Judge Prompt
2. 固定 Judge 模型
3. 保存原始 Judge 输出
4. 保存评估原因
5. 支持人工复核
```

### 2.3 为什么第一版不引入 Python/RAGAS 服务

RAGAS 的指标体系可以作为参考，但第一版不建议引入 Python 服务。

原因：

```text
1. 当前项目是 Java/Spring Boot 框架。
2. 引入 Python 会增加部署复杂度。
3. 第一版主要目标是打通评估闭环。
4. 检索指标完全可以 Java 自研。
5. 生成指标可以用 Spring AI 调用 LLM Judge 实现。
```

后续如果需要对齐 RAGAS，可以单独做：

```text
trust-rag-evaluation-ragas-adapter
```

但不作为当前固定方案。

---

## 3. 模块边界

新增模块：

```text
trust-rag-evaluation
```

该模块只负责评估，不负责知识入库、检索实现、问答生成。

它依赖现有能力：

```text
TrustRagEngine
RagTraceRepository
RetrievalLogRepository
KnowledgeRepository
FeedbackRepository
ReviewTaskRepository
```

它提供新能力：

```text
测试集管理
测试样例管理
评估任务执行
检索指标计算
LLM Judge 生成质量评估
知识治理指标统计
Before/After 对比
评估报告生成
评估看板 API
```

---

## 4. 总体架构

```text
前端评估页面
  ↓
Evaluation Admin API
  ↓
EvalDatasetService
EvalCaseService
EvalRunService
  ↓
EvalRunner / Spring Batch Job
  ↓
TrustRagEngine.ask(evaluationMode = true)
  ↓
RagAnswer + RagTrace + RetrievalLog
  ↓
RetrievalMetricCalculator
GenerationJudgeService
GovernanceMetricService
  ↓
EvalResult
EvalReport
EvalCompareReport
  ↓
ECharts 指标看板
```

---

## 5. 核心业务流程

## 5.1 标准测试集评估流程

```text
创建测试集
  ↓
添加测试样例
  ↓
创建 EvalRun
  ↓
异步执行评估任务
  ↓
逐条调用 TrustRagEngine
  ↓
保存实际回答和召回结果
  ↓
计算 Recall@K / Precision@K / MRR / NDCG
  ↓
调用 LLM Judge 计算 Faithfulness / Correctness / Relevance
  ↓
保存 EvalResult
  ↓
聚合生成 EvalReport
```

---

## 5.2 Before/After 对比流程

```text
选择测试集
  ↓
知识入库前运行评估，标记 BEFORE
  ↓
候选知识审核并进入 high
  ↓
知识入库后运行同一测试集，标记 AFTER
  ↓
选择 before_run 和 after_run
  ↓
生成对比报告
  ↓
输出指标变化和结论
```

---

## 5.3 知识治理指标统计流程

```text
读取 knowledge_item
读取 review_task
读取 retrieval_log
读取 feedback
读取 privacy_event
读取 knowledge_lineage
  ↓
统计候选通过率
统计知识复用率
统计污染率
统计隐私泄露率
统计缺口解决率
  ↓
生成治理指标快照
```

---

## 6. evaluationMode 设计

评估任务会调用 `TrustRagEngine.ask()`，但评估调用不能影响正常知识飞轮。

需要在 `RagRequest` 中新增字段：

```java
private Boolean evaluationMode;
private String evalRunId;
private String evalCaseId;
```

当 `evaluationMode = true` 时，Engine 必须禁用以下行为：

```text
1. 不触发候选知识抽取
2. 不创建 gap_event
3. 不更新 usage_count
4. 不触发 promotion_task
5. 不写用户反馈
6. 不影响知识热度
7. Trace 可以保存，但必须标记 trace_type = EVAL
```

推荐新增：

```java
public enum TraceType {
    NORMAL,
    EVAL
}
```

Trace 表中新增字段：

```sql
ALTER TABLE rag_trace ADD COLUMN trace_type VARCHAR(32) DEFAULT 'NORMAL';
ALTER TABLE rag_trace ADD COLUMN eval_run_id BIGINT;
ALTER TABLE rag_trace ADD COLUMN eval_case_id BIGINT;
```

---

## 7. 数据库设计

## 7.1 eval_dataset：测试集表

```sql
CREATE TABLE eval_dataset (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    created_by VARCHAR(64),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.2 eval_case：测试样例表

```sql
CREATE TABLE eval_case (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL,

    question TEXT NOT NULL,
    expected_answer TEXT,

    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),

    tags TEXT,
    difficulty VARCHAR(32),
    enabled BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

说明：

```text
question：评估问题
expected_answer：标准答案，用于 Answer Correctness
租户 / 项目 / 用户字段：用于模拟真实权限和 Scope
```

---

## 7.3 eval_case_expected_knowledge：标准知识表

不建议把 expected_knowledge_ids 直接用字符串塞在 eval_case 中。为了方便计算和维护，单独建表。

```sql
CREATE TABLE eval_case_expected_knowledge (
    id BIGSERIAL PRIMARY KEY,
    eval_case_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    relevance_grade INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

字段说明：

| 字段 | 说明 |
|---|---|
| knowledge_id | 标准应该召回的知识 ID |
| relevance_grade | 相关性等级，默认 1；NDCG 可用 0/1/2/3 |

---

## 7.4 eval_run：评估任务表

```sql
CREATE TABLE eval_run (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL,

    run_name VARCHAR(255),
    run_type VARCHAR(32),
    before_after_group VARCHAR(32),

    status VARCHAR(32),
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,

    engine_config_snapshot TEXT,
    model_config_snapshot TEXT,
    knowledge_snapshot_time TIMESTAMP,

    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### run_type

```text
MANUAL
SCHEDULED
BEFORE_AFTER
REGRESSION
```

### before_after_group

```text
NORMAL
BEFORE
AFTER
```

### status

```text
PENDING
RUNNING
SUCCESS
FAILED
CANCELED
```

---

## 7.5 eval_result：单题评估结果表

```sql
CREATE TABLE eval_result (
    id BIGSERIAL PRIMARY KEY,

    eval_run_id BIGINT NOT NULL,
    eval_case_id BIGINT NOT NULL,
    trace_id VARCHAR(64),

    question TEXT,
    expected_answer TEXT,
    actual_answer TEXT,

    retrieved_knowledge_ids TEXT,
    used_knowledge_ids TEXT,

    recall_at_5 NUMERIC(5,4),
    recall_at_10 NUMERIC(5,4),
    precision_at_5 NUMERIC(5,4),
    precision_at_10 NUMERIC(5,4),
    mrr NUMERIC(5,4),
    ndcg_at_5 NUMERIC(5,4),
    ndcg_at_10 NUMERIC(5,4),

    faithfulness NUMERIC(5,4),
    answer_correctness NUMERIC(5,4),
    answer_relevance NUMERIC(5,4),
    hallucination_score NUMERIC(5,4),

    latency_ms BIGINT,
    prompt_tokens INT,
    completion_tokens INT,

    status VARCHAR(32),
    error_message TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.6 eval_judge_detail：LLM Judge 详情表

```sql
CREATE TABLE eval_judge_detail (
    id BIGSERIAL PRIMARY KEY,

    eval_result_id BIGINT NOT NULL,
    judge_type VARCHAR(64),
    judge_model VARCHAR(128),
    judge_prompt TEXT,
    judge_output TEXT,
    score NUMERIC(5,4),
    reason TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

judge_type：

```text
FAITHFULNESS
ANSWER_CORRECTNESS
ANSWER_RELEVANCE
```

说明：

```text
必须保存 judge_output 原文，方便排查 LLM Judge 是否误判。
```

---

## 7.7 eval_report：评估报告表

```sql
CREATE TABLE eval_report (
    id BIGSERIAL PRIMARY KEY,
    eval_run_id BIGINT NOT NULL,

    avg_recall_at_5 NUMERIC(5,4),
    avg_recall_at_10 NUMERIC(5,4),
    avg_precision_at_5 NUMERIC(5,4),
    avg_precision_at_10 NUMERIC(5,4),
    avg_mrr NUMERIC(5,4),
    avg_ndcg_at_5 NUMERIC(5,4),
    avg_ndcg_at_10 NUMERIC(5,4),

    avg_faithfulness NUMERIC(5,4),
    avg_answer_correctness NUMERIC(5,4),
    avg_answer_relevance NUMERIC(5,4),
    avg_hallucination_score NUMERIC(5,4),

    avg_latency_ms BIGINT,
    total_prompt_tokens BIGINT,
    total_completion_tokens BIGINT,

    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.8 eval_compare_report：Before/After 对比报告表

```sql
CREATE TABLE eval_compare_report (
    id BIGSERIAL PRIMARY KEY,

    before_run_id BIGINT NOT NULL,
    after_run_id BIGINT NOT NULL,

    recall_at_5_before NUMERIC(5,4),
    recall_at_5_after NUMERIC(5,4),
    recall_at_5_delta NUMERIC(5,4),

    mrr_before NUMERIC(5,4),
    mrr_after NUMERIC(5,4),
    mrr_delta NUMERIC(5,4),

    faithfulness_before NUMERIC(5,4),
    faithfulness_after NUMERIC(5,4),
    faithfulness_delta NUMERIC(5,4),

    answer_correctness_before NUMERIC(5,4),
    answer_correctness_after NUMERIC(5,4),
    answer_correctness_delta NUMERIC(5,4),

    hallucination_before NUMERIC(5,4),
    hallucination_after NUMERIC(5,4),
    hallucination_delta NUMERIC(5,4),

    conclusion TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7.9 eval_governance_snapshot：知识治理指标快照表

```sql
CREATE TABLE eval_governance_snapshot (
    id BIGSERIAL PRIMARY KEY,

    snapshot_date DATE NOT NULL,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),

    candidate_count INT DEFAULT 0,
    approved_candidate_count INT DEFAULT 0,
    candidate_approval_rate NUMERIC(5,4),

    new_high_knowledge_count INT DEFAULT 0,
    reused_knowledge_count INT DEFAULT 0,
    knowledge_reuse_rate NUMERIC(5,4),

    contaminated_knowledge_count INT DEFAULT 0,
    high_knowledge_count INT DEFAULT 0,
    contamination_rate NUMERIC(5,4),

    privacy_event_count INT DEFAULT 0,
    qa_count INT DEFAULT 0,
    privacy_leakage_rate NUMERIC(5,4),

    gap_event_count INT DEFAULT 0,
    resolved_gap_count INT DEFAULT 0,
    gap_resolve_rate NUMERIC(5,4),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 8. 检索指标设计

检索指标输入：

```text
expectedKnowledgeIds：标准知识 ID
retrievedKnowledgeIds：实际召回知识 ID，按排序排列
```

---

## 8.1 Recall@K

含义：标准知识有多少被 TopK 召回。

```text
Recall@K = |TopK ∩ Expected| / |Expected|
```

Java 接口：

```java
public interface RetrievalMetricCalculator {
    RetrievalMetricResult calculate(List<Long> expectedIds, List<Long> retrievedIds);
}
```

---

## 8.2 Precision@K

含义：TopK 中有多少是标准相关知识。

```text
Precision@K = |TopK ∩ Expected| / K
```

注意：

```text
即使实际召回数量少于 K，分母仍然用 K。
这样可以惩罚召回不足。
```

---

## 8.3 MRR

含义：第一个正确知识排得越靠前，分数越高。

```text
MRR = 1 / 第一个相关结果的排名
```

如果没有相关结果：

```text
MRR = 0
```

---

## 8.4 NDCG@K

NDCG 衡量排序质量。

如果 `relevance_grade` 只使用 0/1，则是二值相关性。后续可以扩展为 0/1/2/3。

```text
DCG@K = Σ (2^rel_i - 1) / log2(i + 1)
NDCG@K = DCG@K / IDCG@K
```

---

## 8.5 RetrievalMetricResult

```java
public class RetrievalMetricResult {
    private double recallAt5;
    private double recallAt10;
    private double precisionAt5;
    private double precisionAt10;
    private double mrr;
    private double ndcgAt5;
    private double ndcgAt10;
}
```

---

## 9. 生成质量指标设计

生成质量指标使用 LLM-as-Judge。

固定三个 Judge：

```text
FaithfulnessJudge
AnswerCorrectnessJudge
AnswerRelevanceJudge
```

幻觉率第一版使用：

```text
hallucination_score = 1 - faithfulness
```

---

## 9.1 Faithfulness

目标：判断回答是否忠于检索上下文。

输入：

```text
question
retrieved_contexts
actual_answer
```

输出：

```json
{
  "score": 0.0,
  "reason": "原因",
  "unsupported_claims": ["无依据声明"]
}
```

Prompt：

```text
你是一个 RAG 回答忠实度评估器。

给定：
1. 用户问题
2. 检索上下文
3. 模型回答

请判断模型回答中的关键事实是否都能从检索上下文中得到支持。

评分规则：
1.0：所有关键事实都被上下文支持
0.7：大部分事实被支持，只有少量细节缺失
0.4：部分事实被支持，但存在明显无依据内容
0.0：大部分内容无法由上下文支持

请只输出 JSON：
{
  "score": 0.0 到 1.0,
  "reason": "原因",
  "unsupported_claims": ["无依据声明1", "无依据声明2"]
}
```

---

## 9.2 Answer Correctness

目标：判断实际回答是否覆盖标准答案中的核心要点。

输入：

```text
question
expected_answer
actual_answer
```

输出：

```json
{
  "score": 0.0,
  "reason": "原因",
  "missing_points": ["缺失要点"],
  "wrong_points": ["错误点"]
}
```

Prompt：

```text
你是一个答案正确性评估器。

给定：
1. 用户问题
2. 标准答案
3. 模型实际回答

请判断模型实际回答是否正确覆盖标准答案中的核心要点。

评分规则：
1.0：完全正确，覆盖所有核心要点
0.7：基本正确，遗漏少量要点
0.4：部分正确，但遗漏或错误较多
0.0：基本错误

请只输出 JSON：
{
  "score": 0.0 到 1.0,
  "reason": "原因",
  "missing_points": ["缺失要点"],
  "wrong_points": ["错误点"]
}
```

---

## 9.3 Answer Relevance

目标：判断回答是否真正回答了用户问题，而不是答非所问。

输入：

```text
question
actual_answer
```

输出：

```json
{
  "score": 0.0,
  "reason": "原因"
}
```

---

## 9.4 LLM Judge 工程要求

必须满足：

```text
1. Judge 输出必须是 JSON
2. JSON 解析失败时要重试一次
3. 重试失败则该指标 status = FAILED
4. 保存 judge_prompt
5. 保存 judge_output
6. 保存 judge_model
7. 不要把 Judge 结果当作绝对真理
8. 支持人工复核
```

---

## 10. 知识治理指标设计

知识治理指标不依赖单个 eval_case，而是基于系统运行数据统计。

---

## 10.1 Candidate Approval Rate 候选通过率

```text
候选通过率 = 审核通过候选知识数 / 候选知识总数
```

候选知识范围：

```text
source_type in ('user_correction', 'tool_result', 'llm_summary', 'manual_candidate')
```

---

## 10.2 Knowledge Reuse Rate 知识复用率

```text
知识复用率 = 被后续回答使用过的新增知识数 / 新增知识总数
```

判断“被使用”：

```text
retrieval_log.used_in_prompt = true
AND retrieval_log.knowledge_id = 新增知识 ID
```

---

## 10.3 Contamination Rate 知识污染率

```text
污染率 = 被回滚 / 降级 / 标错的 high 知识数 / high 知识总数
```

需要依赖：

```text
knowledge_lineage
knowledge_item.status
review_task
```

---

## 10.4 Privacy Leakage Rate 隐私泄露率

```text
隐私泄露率 = privacy_event 数量 / QA 总数
```

需要新增表：

```sql
CREATE TABLE privacy_event (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64),
    knowledge_id BIGINT,
    event_type VARCHAR(64),
    risk_level VARCHAR(32),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 10.5 Gap Resolve Rate 缺口解决率

```text
缺口解决率 = resolved gap_event 数量 / gap_event 总数
```

用于判断知识飞轮是否真正解决问题。

---

## 11. EvalRunner 设计

## 11.1 核心接口

```java
public interface EvalRunner {
    void run(Long evalRunId);
}
```

## 11.2 执行流程

```text
读取 eval_run
  ↓
读取 eval_case 列表
  ↓
更新 eval_run.status = RUNNING
  ↓
逐条执行 case
  ↓
构造 RagRequest(evaluationMode = true)
  ↓
调用 TrustRagEngine.ask()
  ↓
读取 RagAnswer 和 traceId
  ↓
读取 retrieval_log
  ↓
计算检索指标
  ↓
调用 LLM Judge
  ↓
保存 eval_result
  ↓
聚合生成 eval_report
  ↓
更新 eval_run.status = SUCCESS
```

---

## 11.3 Spring Batch Job 设计

Job：

```text
trustRagEvalJob
```

Step：

```text
loadEvalCasesStep
runRagEvaluationStep
calculateMetricsStep
generateReportStep
```

第一版可以先用 `ThreadPoolTaskExecutor` 简化实现，后续再标准化为 Spring Batch。

建议路线：

```text
第一版：ThreadPoolTaskExecutor
第二版：Spring Batch Job
```

但是技术栈固定保留 Spring Batch，后续用于大规模评估任务。

---

## 12. API 设计

## 12.1 测试集 API

```http
POST   /trust-rag/admin/eval/datasets
GET    /trust-rag/admin/eval/datasets
GET    /trust-rag/admin/eval/datasets/{id}
PUT    /trust-rag/admin/eval/datasets/{id}
DELETE /trust-rag/admin/eval/datasets/{id}
```

---

## 12.2 测试样例 API

```http
POST   /trust-rag/admin/eval/cases
GET    /trust-rag/admin/eval/cases?datasetId=1
PUT    /trust-rag/admin/eval/cases/{id}
DELETE /trust-rag/admin/eval/cases/{id}
POST   /trust-rag/admin/eval/cases/import-csv
```

CSV 字段：

```text
question,expected_answer,expected_knowledge_ids,tags,difficulty
```

---

## 12.3 评估任务 API

```http
POST /trust-rag/admin/eval/runs
GET  /trust-rag/admin/eval/runs
GET  /trust-rag/admin/eval/runs/{id}
POST /trust-rag/admin/eval/runs/{id}/cancel
```

创建评估任务请求：

```json
{
  "datasetId": 1,
  "runName": "Hybrid Search v1 评估",
  "runType": "MANUAL",
  "beforeAfterGroup": "NORMAL"
}
```

---

## 12.4 结果与报告 API

```http
GET /trust-rag/admin/eval/runs/{id}/results
GET /trust-rag/admin/eval/runs/{id}/report
```

---

## 12.5 Before/After 对比 API

```http
POST /trust-rag/admin/eval/compare
```

请求：

```json
{
  "beforeRunId": 1001,
  "afterRunId": 1002
}
```

---

## 12.6 治理指标 API

```http
GET /trust-rag/admin/eval/governance/summary
GET /trust-rag/admin/eval/governance/trend
POST /trust-rag/admin/eval/governance/snapshot
```

---

## 13. 配置设计

```yaml
trust-rag:
  evaluation:
    enabled: true
    runner:
      thread-pool-size: 4
      case-timeout-seconds: 120
      save-eval-trace: true
    metrics:
      recall-k-values: [5, 10]
      precision-k-values: [5, 10]
      ndcg-k-values: [5, 10]
    judge:
      enabled: true
      model: qwen-plus
      temperature: 0
      max-retry: 1
      save-prompt: true
      save-output: true
    governance:
      snapshot-enabled: true
      snapshot-cron: "0 0 2 * * ?"
```

---

## 14. 前端页面设计

评估模块前端路径：

```text
/eval/datasets
/eval/cases
/eval/runs
/eval/reports
/eval/compare
/eval/governance
```

---

## 14.1 测试集管理页

功能：

```text
创建测试集
编辑测试集
删除测试集
查看样例数量
查看最近一次评估结果
```

---

## 14.2 测试样例管理页

功能：

```text
手动添加问题
填写标准答案
绑定 expected knowledge
CSV 导入
按标签和难度过滤
```

---

## 14.3 评估任务页

功能：

```text
选择测试集
创建评估任务
查看任务状态
查看成功/失败数量
查看耗时
取消任务
```

---

## 14.4 评估报告页

展示：

```text
Recall@5 / Recall@10
Precision@5 / Precision@10
MRR
NDCG@5 / NDCG@10
Faithfulness
Answer Correctness
Answer Relevance
Hallucination Score
逐题明细
Judge 原始输出
```

---

## 14.5 Before/After 对比页

展示：

```text
before_run
after_run
指标变化表
柱状图
趋势图
自动结论
```

---

## 14.6 知识治理看板

展示：

```text
候选通过率
知识复用率
污染率
隐私泄露率
缺口解决率
每日趋势
```

---

## 15. 开发路线

## 阶段 1：评估数据模型与 API

目标：先能管理测试集。

任务：

```text
1. 创建 eval_dataset
2. 创建 eval_case
3. 创建 eval_case_expected_knowledge
4. 实现测试集 CRUD
5. 实现测试样例 CRUD
6. 实现 CSV 导入
```

验收：

```text
可以创建测试集，并为每个问题绑定标准答案和标准知识 ID。
```

---

## 阶段 2：EvalRunner 与检索指标

目标：跑通最稳定的客观评估。

任务：

```text
1. 创建 eval_run
2. 创建 eval_result
3. 实现 EvalRunner
4. 调用 TrustRagEngine.ask(evaluationMode=true)
5. 获取 retrievedKnowledgeIds
6. 实现 Recall@K
7. 实现 Precision@K
8. 实现 MRR
9. 实现 NDCG@K
10. 生成 eval_report
```

验收：

```text
给定一个测试集，可以运行评估并生成检索指标报告。
```

---

## 阶段 3：LLM Judge 生成质量评估

目标：评估回答质量。

任务：

```text
1. 实现 FaithfulnessJudge
2. 实现 AnswerCorrectnessJudge
3. 实现 AnswerRelevanceJudge
4. 实现 hallucination_score = 1 - faithfulness
5. 保存 eval_judge_detail
6. 处理 JSON 解析失败和重试
```

验收：

```text
每条 eval_result 都有 Faithfulness、Answer Correctness、Answer Relevance、Hallucination Score。
```

---

## 阶段 4：Before/After 对比

目标：证明知识入库后是否变好。

任务：

```text
1. 支持 before_after_group
2. 实现 compare API
3. 创建 eval_compare_report
4. 计算指标 delta
5. 生成对比结论
```

验收：

```text
可以选择两个 eval_run，生成 Before/After 对比报告。
```

---

## 阶段 5：知识治理指标

目标：评估知识飞轮质量。

任务：

```text
1. 候选通过率
2. 知识复用率
3. 污染率
4. 隐私泄露率
5. 缺口解决率
6. eval_governance_snapshot
```

验收：

```text
可以看到知识飞轮是否健康，而不只是看问答质量。
```

---

## 阶段 6：前端评估页面

目标：产品化展示。

任务：

```text
1. 测试集页面
2. 测试样例页面
3. 评估任务页面
4. 评估报告页面
5. Before/After 对比页面
6. 知识治理看板
```

验收：

```text
管理员可以在页面上创建测试集、运行评估、查看报告和治理指标。
```

---

## 16. 工程风险与处理

### 16.1 评估污染知识库

风险：评估调用触发知识缺口、候选知识、usage_count。

处理：

```text
evaluationMode = true
禁用候选抽取
禁用 gap_event
禁用 usage_count 更新
Trace 标记为 EVAL
```

---

### 16.2 LLM Judge 不稳定

风险：Judge 模型评分波动。

处理：

```text
temperature = 0
固定 Prompt
保存 judge_output
支持人工复核
重要报告可重复运行取平均
```

---

### 16.3 标准知识 ID 维护成本高

风险：每个 eval_case 都要人工绑定 expected knowledge。

处理：

```text
第一版人工维护
第二版支持从历史 trace 推荐 expected knowledge
第三版支持半自动标注
```

---

### 16.4 Before/After 不严谨

风险：评估时知识库状态变化，影响对比。

处理：

```text
记录 engine_config_snapshot
记录 model_config_snapshot
记录 knowledge_snapshot_time
第一版用 before/after 两次评估
后续再做知识快照
```

---

## 17. 最小可用版本范围

第一版必须实现：

```text
eval_dataset
eval_case
eval_case_expected_knowledge
eval_run
eval_result
eval_report
EvalRunner
Recall@K
Precision@K
MRR
NDCG@K
evaluationMode
```

第一版可以暂缓：

```text
LLM Judge
Before/After
治理指标
前端页面
```

但是完整版本必须包含：

```text
Faithfulness
Answer Correctness
Answer Relevance
Hallucination Score
Before/After
候选通过率
知识复用率
污染率
隐私泄露率
评估看板
```

---

## 18. 最终结论

评估系统应该被设计成 TrustRAG 的独立后台模块，而不是主链路的一部分。

固定方案为：

```text
Spring Boot + Spring Batch + Spring AI LLM-as-Judge + PostgreSQL + Vue3/ECharts
```

实现路线为：

```text
测试集管理
  ↓
EvalRunner
  ↓
检索指标
  ↓
生成质量 Judge
  ↓
Before/After
  ↓
知识治理指标
  ↓
前端评估看板
```

最优先要做的是：

```text
eval_dataset + eval_case + eval_run + eval_result + Recall@K + Precision@K + MRR + NDCG
```

因为这部分不依赖 LLM，结果稳定，能最快建立评估模块骨架。

等检索评估稳定后，再接入 LLM Judge 和知识治理指标。这样风险最低，工程推进最稳。
