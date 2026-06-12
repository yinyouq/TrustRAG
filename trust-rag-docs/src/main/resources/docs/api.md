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

非法参数或状态返回 400，知识不存在返回 404，数据库、向量库或模型等系统错误返回 500。
