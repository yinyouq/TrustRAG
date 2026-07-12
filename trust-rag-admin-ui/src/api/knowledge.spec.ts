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

  it('calls knowledge lifecycle governance endpoints', async () => {
    const bodies: Record<string, unknown> = {}
    let listUrl = ''
    let getUrl = ''
    server.use(
      http.get('*/trust-rag/admin/knowledge', ({ request }) => {
        listUrl = request.url
        return HttpResponse.json([{
          id: 88,
          title: '错误知识',
          content: '旧内容',
          trustLevel: 'HIGH',
          status: 'HIGH_ENABLED',
          scopeType: 'GLOBAL',
        }])
      }),
      http.get('*/trust-rag/admin/knowledge/88', ({ request }) => {
        getUrl = request.url
        return HttpResponse.json({
          id: 88,
          title: '错误知识',
          content: '旧内容',
          trustLevel: 'HIGH',
          status: 'HIGH_ENABLED',
          scopeType: 'GLOBAL',
        })
      }),
      http.post('*/trust-rag/admin/knowledge/88/downgrade', async ({ request }) => {
        bodies.downgrade = await request.json()
        return HttpResponse.json({ id: 88, trustLevel: 'MEDIUM', status: 'MEDIUM_ENABLED' })
      }),
      http.post('*/trust-rag/admin/knowledge/88/rollback', async ({ request }) => {
        bodies.rollback = await request.json()
        return HttpResponse.json({ id: 88, trustLevel: 'HIGH', status: 'HIGH_ENABLED' })
      }),
      http.post('*/trust-rag/admin/knowledge/merge', async ({ request }) => {
        bodies.merge = await request.json()
        return HttpResponse.json({ id: 99, trustLevel: 'HIGH', status: 'HIGH_ENABLED' })
      }),
      http.delete('*/trust-rag/admin/knowledge/88', async ({ request }) => {
        bodies.delete = await request.json()
        return HttpResponse.json({ id: 88, trustLevel: 'HIGH', status: 'REJECTED' })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const listed = await api.listKnowledge({
      trustLevel: 'HIGH',
      status: 'HIGH_ENABLED',
      limit: 10,
      offset: 20,
    })
    const current = await api.getKnowledge(88)
    await api.downgradeKnowledge(88, { operatorId: 'admin', reason: '错误' })
    await api.rollbackKnowledge(88, { operatorId: 'admin', reason: '恢复' })
    await api.mergeKnowledge({ operatorId: 'admin', targetKnowledgeId: 99, sourceKnowledgeIds: [88] })
    await api.deleteKnowledge(88, { operatorId: 'admin', reason: '删除错误知识' })

    const search = new URL(listUrl).searchParams
    expect(search.get('trustLevel')).toBe('HIGH')
    expect(search.get('status')).toBe('HIGH_ENABLED')
    expect(search.get('limit')).toBe('10')
    expect(search.get('offset')).toBe('20')
    expect(getUrl).toContain('/trust-rag/admin/knowledge/88')
    expect(listed[0].id).toBe(88)
    expect(current.id).toBe(88)
    expect(bodies.downgrade).toEqual({ operatorId: 'admin', reason: '错误' })
    expect(bodies.rollback).toEqual({ operatorId: 'admin', reason: '恢复' })
    expect(bodies.merge).toEqual({ operatorId: 'admin', targetKnowledgeId: 99, sourceKnowledgeIds: [88] })
    expect(bodies.delete).toEqual({ operatorId: 'admin', reason: '删除错误知识' })
  })

  it('runs promotion tasks through admin governance endpoint', async () => {
    let promotionUrl = ''
    server.use(
      http.post('*/trust-rag/admin/promotion/run', ({ request }) => {
        promotionUrl = request.url
        return HttpResponse.json({ createdTasks: 2, processedTasks: 1 })
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const result = await api.runPromotion(25)

    const search = new URL(promotionUrl).searchParams
    expect(search.get('limit')).toBe('25')
    expect(result).toEqual({ createdTasks: 2, processedTasks: 1 })
  })

  it('lists promotion task records through admin governance endpoint', async () => {
    let taskUrl = ''
    server.use(
      http.get('*/trust-rag/admin/promotion/tasks', ({ request }) => {
        taskUrl = request.url
        return HttpResponse.json([{
          id: 501,
          knowledgeId: 88,
          status: 'FAILED',
          taskType: 'LOW_TO_MEDIUM',
          retryCount: 1,
          errorMessage: 'index failed',
          startedAt: '2026-06-20T12:00:00Z',
          finishedAt: '2026-06-20T12:00:01Z',
          createdAt: '2026-06-20T12:00:00Z',
        }])
      }),
    )
    const api = createKnowledgeApi(axios.create())

    const tasks = await api.listPromotionTasks({
      status: 'FAILED',
      taskType: 'LOW_TO_MEDIUM',
      knowledgeId: 88,
      limit: 10,
      offset: 5,
    })

    const search = new URL(taskUrl).searchParams
    expect(search.get('status')).toBe('FAILED')
    expect(search.get('taskType')).toBe('LOW_TO_MEDIUM')
    expect(search.get('knowledgeId')).toBe('88')
    expect(search.get('limit')).toBe('10')
    expect(search.get('offset')).toBe('5')
    expect(tasks[0].errorMessage).toBe('index failed')
  })
})
