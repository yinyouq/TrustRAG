/**
 * 验证知识库管理 API 的路径、请求体和 multipart 上传行为。
 */
import axios from 'axios'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { createKnowledgeApi } from './knowledge'
import { server } from '@/tests/testServer'

describe('knowledge API', () => {
  it('uploads a document with metadata as multipart data', async () => {
    let contentType = ''
    let taskUrl = ''
    server.use(
      http.post('*/api/documents/upload', async ({ request }) => {
        contentType = request.headers.get('content-type') ?? ''
        const body = await request.formData()
        expect(body.get('title')).toBe('产品手册')
        expect(body.get('sourceType')).toBe('internal_doc')
        expect(body.get('trustLevel')).toBe('HIGH')
        expect(body.get('scopeType')).toBe('GLOBAL')
        expect(body.get('file')).toBeTruthy()
        return HttpResponse.json({ taskId: 'task-1', status: 'PENDING' }, { status: 202 })
      }),
      http.get('*/api/documents/tasks/task-1', ({ request }) => {
        taskUrl = request.url
        return HttpResponse.json({ taskId: 'task-1', status: 'COMPLETED' })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const task = await api.uploadDocument({
      file: new File(['hello'], 'manual.md', { type: 'text/markdown' }),
      title: '产品手册',
      sourceType: 'internal_doc',
      trustLevel: 'HIGH',
      scopeType: 'GLOBAL',
    })
    const queried = await api.getDocumentTask(task.taskId)

    expect(contentType).toContain('multipart/form-data')
    expect(queried.status).toBe('COMPLETED')
    expect(taskUrl).toContain('/api/documents/tasks/task-1')
  })

  it('imports manual knowledge through admin knowledge endpoint', async () => {
    let importBody: unknown
    server.use(
      http.post('*/trust-rag/admin/knowledge/import', async ({ request }) => {
        importBody = await request.json()
        return HttpResponse.json({
          importedCount: 1,
          duplicateCount: 0,
          failedCount: 0,
          knowledgeIds: [101],
        }, { status: 201 })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const result = await api.importKnowledge({
      title: '部署说明',
      content: '生产环境需要配置 EmbeddingModel。',
      sourceType: 'manual',
      sourceRef: 'manual://deploy',
      trustLevel: 'HIGH',
      scopeType: 'GLOBAL',
    })

    expect(importBody).toEqual({
      title: '部署说明',
      content: '生产环境需要配置 EmbeddingModel。',
      sourceType: 'manual',
      sourceRef: 'manual://deploy',
      trustLevel: 'HIGH',
      scopeType: 'GLOBAL',
    })
    expect(result.knowledgeIds).toEqual([101])
    expect(result.importedCount).toBe(1)
  })

  it('lists document libraries and knowledge references', async () => {
    let listUrl = ''
    let knowledgeUrl = ''
    server.use(
      http.get('*/api/documents/tasks', ({ request }) => {
        listUrl = request.url
        return HttpResponse.json([{
          taskId: 'task-1',
          title: 'Java 八股知识库',
          status: 'COMPLETED',
          projectId: 'jv',
        }])
      }),
      http.get('*/api/documents/tasks/task-1/knowledge', ({ request }) => {
        knowledgeUrl = request.url
        return HttpResponse.json({
          task: { taskId: 'task-1', title: 'Java 八股知识库', status: 'COMPLETED' },
          items: [{
            knowledgeId: 91,
            expectedKnowledgeIds: '91',
            title: 'String 不可变',
            contentPreview: 'String 不可变的原因...',
            content: '完整知识内容',
            status: 'HIGH_ENABLED',
          }],
          totalCount: 1,
        })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const tasks = await api.listDocumentTasks({ limit: 20, offset: 5 })
    const knowledge = await api.listDocumentKnowledge('task-1', { limit: 200, offset: 0 })

    expect(new URL(listUrl).searchParams.get('limit')).toBe('20')
    expect(new URL(listUrl).searchParams.get('offset')).toBe('5')
    expect(knowledgeUrl).toContain('/api/documents/tasks/task-1/knowledge')
    expect(tasks[0].projectId).toBe('jv')
    expect(knowledge.items[0].knowledgeId).toBe(91)
  })

  it('reviews medium knowledge candidates through admin endpoints', async () => {
    let listUrl = ''
    let approveBody: unknown
    let rejectBody: unknown
    server.use(
      http.get('*/trust-rag/admin/knowledge/candidates', ({ request }) => {
        listUrl = request.url
        return HttpResponse.json([{
          id: 12,
          title: '中可信知识',
          content: '需要人工审核的内容',
          trustLevel: 'MEDIUM',
          status: 'HUMAN_REVIEW_PENDING',
          scopeType: 'GLOBAL',
          createdAt: '2026-06-20T12:00:00Z',
          updatedAt: '2026-06-20T12:00:00Z',
        }])
      }),
      http.post('*/trust-rag/admin/knowledge/12/approve-high', async ({ request }) => {
        approveBody = await request.json()
        return HttpResponse.json({ id: 12, trustLevel: 'HIGH', status: 'HIGH_ENABLED' })
      }),
      http.post('*/trust-rag/admin/knowledge/13/reject', async ({ request }) => {
        rejectBody = await request.json()
        return HttpResponse.json({ id: 13, trustLevel: 'MEDIUM', status: 'REJECTED' })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const candidates = await api.listReviewCandidates({ limit: 20, offset: 5 })
    await api.approveHigh(12, { reviewerId: 'admin', comment: '内容已确认' })
    await api.rejectCandidate(13, { reviewerId: 'admin', comment: '来源不足' })

    const search = new URL(listUrl).searchParams
    expect(search.get('status')).toBe('HUMAN_REVIEW_PENDING')
    expect(search.get('trustLevel')).toBe('MEDIUM')
    expect(search.get('limit')).toBe('20')
    expect(search.get('offset')).toBe('5')
    expect(candidates[0].id).toBe(12)
    expect(approveBody).toEqual({ reviewerId: 'admin', comment: '内容已确认' })
    expect(rejectBody).toEqual({ reviewerId: 'admin', comment: '来源不足' })
  })
})
