# HTTP API

管理 API 仅在 `trust-rag.admin-api.enabled=true` 且应用为 Servlet Web 应用时注册。
生产环境必须为 `/trust-rag/**` 配置身份认证和管理权限。

## 问答示例

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

`CORRECTION` 必须提供 `correctedAnswer`。普通 `LIKE`、`DISLIKE` 等反馈只落库，
不会生成候选。

## 查询候选

```http
GET /trust-rag/admin/knowledge/candidates
    ?status=PENDING_REVIEW
    &trustLevel=LOW
    &scopeType=GLOBAL_CANDIDATE
    &limit=50
    &offset=0
```

`trustLevel` 和 `scopeType` 可省略，`limit` 最大为 200。

## 手动创建候选

```http
POST /trust-rag/admin/knowledge/candidates
```

```json
{
  "title": "候选标题",
  "claim": "核心结论",
  "content": "待审核的知识正文",
  "evidence": "来源证据",
  "sourceRef": "ticket://123",
  "scopeType": "GLOBAL_CANDIDATE",
  "tags": ["manual"]
}
```

## 导入知识

```http
POST /trust-rag/admin/knowledge/import
```

```json
{
  "title": "官方文档",
  "content": "经过确认的文档内容",
  "sourceType": "official_document",
  "sourceRef": "https://example.invalid/doc",
  "trustLevel": "HIGH",
  "scopeType": "GLOBAL"
}
```

长文会按 `trust-rag.chunk` 配置切分并逐块向量化。响应包含成功、重复、失败数量和
已创建的知识 ID。

## 审核通过

```http
POST /trust-rag/admin/knowledge/{id}/approve
```

```json
{
  "reviewerId": "admin",
  "comment": "已核验来源",
  "modifiedTitle": null,
  "modifiedContent": null
}
```

审核通过会重新向量化，并将公共候选从 `GLOBAL_CANDIDATE` 转为 `GLOBAL`。

## 审核拒绝

```http
POST /trust-rag/admin/knowledge/{id}/reject
```

```json
{
  "reviewerId": "admin",
  "comment": "证据不足"
}
```

请求参数或状态不合法返回 400，知识不存在返回 404，向量库、数据库或模型调用等
系统错误返回 500。
