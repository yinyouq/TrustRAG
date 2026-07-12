# HTTP API

管理 API 仅在 `trust-rag.admin-api.enabled=true` 且应用为 Servlet Web 应用时注册。生产环境必须为 `/trust-rag/**` 配置身份认证和管理权限。

## 问答

示例应用提供：

```http
POST /api/chat
Content-Type: application/json
```

```json
{
  "question": "TrustRAG 如何处理用户纠错？",
  "userId": "u-1",
  "conversationId": "c-1",
  "projectId": "p-1",
  "tenantId": "t-1"
}
```

## 提交反馈

```http
POST /trust-rag/feedback
```

```json
{
  "traceId": "trace-id",
  "feedbackType": "CORRECTION",
  "feedbackContent": "原回答不准确",
  "correctedAnswer": "经过确认的正确内容",
  "userId": "u-1",
  "conversationId": "c-1"
}
```

`CORRECTION` 必须提供 `correctedAnswer`，并创建 `LOW_PENDING` 候选。所有反馈都会通过 trace 回流到实际用于回答的知识项。

## 候选与导入

### 异步文档上传

```http
POST /api/documents/upload
Content-Type: multipart/form-data
```

表单字段：

- `file`：必填，支持 PDF、DOC/DOCX、HTML、Markdown、MediaWiki 文本以及 Tika 可解析格式。
- `title`：可选的知识标题前缀。
- `sourceUrl`：可选的原始 HTTP/HTTPS 来源地址，用于引用追踪。
- `sourceType`：默认 `document`。
- `trustLevel`：默认 `HIGH`。
- `scopeType`：默认 `GLOBAL`。
- `userId`、`conversationId`、`projectId`、`tenantId`：按作用域填写。

接口返回 `202 Accepted` 和任务 ID。任务由 Spring Scheduling 异步解析、切分、向量化并写入 Milvus/OpenSearch。

```http
GET /api/documents/tasks/{taskId}
POST /api/documents/tasks/{taskId}/retry
```

任务状态为 `PENDING`、`PROCESSING`、`COMPLETED`、`PARTIAL` 或 `FAILED`。

### Git 文档导入

```http
POST /api/documents/git
Content-Type: application/json
```

```json
{
  "repositoryUri": "https://github.com/example/docs.git",
  "gitRef": "main",
  "title": "项目文档",
  "sourceType": "official_doc",
  "trustLevel": "HIGH",
  "scopeType": "GLOBAL"
}
```

远程 Git 需要显式启用并配置主机白名单。本地 Git 只能位于允许目录中；接口不接收或持久化 Git 凭据。

```http
GET /trust-rag/admin/knowledge/candidates
    ?status=HUMAN_REVIEW_PENDING
    &trustLevel=MEDIUM
    &scopeType=GLOBAL
    &limit=50
    &offset=0
```

```http
POST /trust-rag/admin/knowledge/candidates
```

```json
{
  "title": "候选标题",
  "claim": "核心结论",
  "content": "待治理的知识正文",
  "evidence": "来源证据",
  "sourceRef": "ticket://123",
  "scopeType": "GLOBAL_CANDIDATE",
  "tags": ["manual"]
}
```

```http
POST /trust-rag/admin/knowledge/import
```

导入接口支持 `HIGH`、`MEDIUM`、`LOW`。中可信导入会自动创建人工复核任务；低可信导入由定时治理扫描创建晋升任务。

## 晋升治理

手动运行一批治理任务：

```http
POST /trust-rag/admin/promotion/run?limit=50
```

查询任务：

```http
GET /trust-rag/admin/promotion/tasks
    ?status=FAILED
    &taskType=LOW_TO_MEDIUM
    &knowledgeId=123
```

查询冲突：

```http
GET /trust-rag/admin/conflicts?limit=50&offset=0
```

查询中池：

```http
GET /trust-rag/admin/knowledge/medium
```

按信任级别和状态查询知识：

```http
GET /trust-rag/admin/knowledge
    ?trustLevel=MEDIUM
    &status=HUMAN_REVIEW_PENDING
    &limit=50
    &offset=0
```

按 ID 查询单条知识：

```http
GET /trust-rag/admin/knowledge/{id}
```

## 人工终审

```http
POST /trust-rag/admin/knowledge/{id}/approve-high
```

兼容端点 `/approve` 行为相同。

```json
{
  "reviewerId": "admin",
  "comment": "来源和内容已核验",
  "modifiedTitle": null,
  "modifiedContent": null
}
```

拒绝：

```http
POST /trust-rag/admin/knowledge/{id}/reject
```

## 生命周期治理

降级：

```http
POST /trust-rag/admin/knowledge/{id}/downgrade
```

回滚：

```http
POST /trust-rag/admin/knowledge/{id}/rollback
```

删除：

```http
DELETE /trust-rag/admin/knowledge/{id}
```

请求体：

```json
{
  "operatorId": "admin",
  "reason": "来源失效"
}
```

合并：

```http
POST /trust-rag/admin/knowledge/{targetId}/merge
```

```json
{
  "operatorId": "admin",
  "sourceKnowledgeIds": [101, 102]
}
```

也可以在请求体中指定目标知识：

```http
POST /trust-rag/admin/knowledge/merge
```

```json
{
  "operatorId": "admin",
  "targetKnowledgeId": 100,
  "sourceKnowledgeIds": [101, 102]
}
```

Milvus 或 OpenSearch 写入失败时会立即创建 `index_sync_task`。任务按 `index-sync.retry-limit` 重试，成功后恢复到失败前计划进入的可用状态；历史 `INDEX_FAILED` 数据仍可由治理周期中的 `INDEX_RETRY` 兼容处理。

`retrieval_log.search_type` 可能为 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID_RRF` 或 `HYBRID_RRF_RERANK`，并记录 `vector_rank`、`keyword_rank`、`rrf_score`。

非法参数或状态返回 400，知识不存在返回 404，数据库、向量库或模型等系统错误返回 500。

## 评估系统

评估 API 前缀为 `/trust-rag/admin/eval`。

测试集：

```http
POST   /trust-rag/admin/eval/datasets
GET    /trust-rag/admin/eval/datasets
GET    /trust-rag/admin/eval/datasets/{id}
PUT    /trust-rag/admin/eval/datasets/{id}
DELETE /trust-rag/admin/eval/datasets/{id}
```

存在历史 `eval_run` 的测试集不可删除，返回 409。

测试样例：

```http
POST   /trust-rag/admin/eval/cases
GET    /trust-rag/admin/eval/cases?datasetId=1&onlyEnabled=false
GET    /trust-rag/admin/eval/cases/{id}
PUT    /trust-rag/admin/eval/cases/{id}
DELETE /trust-rag/admin/eval/cases/{id}
GET    /trust-rag/admin/eval/cases/{id}/expected-knowledge
PUT    /trust-rag/admin/eval/cases/{id}/expected-knowledge
```

存在历史 `eval_result` 的样例不可删除，返回 409。

CSV 导入使用 `multipart/form-data`：

```http
POST /trust-rag/admin/eval/cases/import-csv?datasetId=1
Content-Disposition: form-data; name="file"; filename="cases.csv"
```

CSV 必须包含 `question`。可选字段为 `expected_answer`、`expected_knowledge_ids`、`tags`、`difficulty`、`tenant_id`、`project_id`、`user_id`、`conversation_id` 和 `enabled`。知识 ID 与标签使用 `|` 分隔。响应会分别返回成功数和带行号的失败原因。

评估任务与报告：

```http
POST /trust-rag/admin/eval/runs
GET  /trust-rag/admin/eval/runs
GET  /trust-rag/admin/eval/runs/{id}
POST /trust-rag/admin/eval/runs/{id}/cancel
GET  /trust-rag/admin/eval/runs/{id}/results
GET  /trust-rag/admin/eval/runs/{id}/report
GET  /trust-rag/admin/eval/results/{resultId}/judge-details
```

Before/After 与治理指标：

```http
POST /trust-rag/admin/eval/compare
GET  /trust-rag/admin/eval/governance/summary
GET  /trust-rag/admin/eval/governance/trend
POST /trust-rag/admin/eval/governance/snapshot
```

`null` 指标表示未执行或 Judge 未启用，不应解释为 0 分。评估任务通过 `evaluationMode=true` 调用引擎，不触发候选知识、知识缺口和使用次数更新。
