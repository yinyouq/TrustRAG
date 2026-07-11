/**
 * MSW 请求处理器，模拟评估管理 API 的主要响应。
 */
import { delay, http, HttpResponse } from 'msw'
import type { EvalCase, EvalDataset, EvalRun } from '@/api/types'
import type { DocumentImportTask, ReviewPayload } from '@/api/knowledge'
import { mockCases, mockDatasets, mockDocumentTasks, mockExpected, mockGovernance, mockJudgeDetails, mockKnowledgeReferences, mockReports, mockResults, mockReviewCandidates, mockRuns } from './fixtures'

let nextDatasetId = 20
let nextCaseId = 50
let nextRunId = 200
let nextDocumentTaskId = 2

export const handlers = [
  http.post('*/api/documents/upload', async ({ request }) => {
    const body = await request.formData()
    const file = body.get('file') as File | null
    const now = new Date().toISOString()
    const task: DocumentImportTask = {
      taskId: `mock-doc-task-${nextDocumentTaskId++}`,
      sourceKind: 'UPLOAD',
      status: 'PROCESSING',
      title: body.get('title')?.toString() || null,
      originalFilename: file?.name || 'uploaded-document',
      sourceUri: body.get('sourceUrl')?.toString() || null,
      sourceType: body.get('sourceType')?.toString() || 'document',
      trustLevel: body.get('trustLevel')?.toString() as DocumentImportTask['trustLevel'] || 'HIGH',
      scopeType: body.get('scopeType')?.toString() as DocumentImportTask['scopeType'] || 'GLOBAL',
      userId: body.get('userId')?.toString() || null,
      conversationId: body.get('conversationId')?.toString() || null,
      projectId: body.get('projectId')?.toString() || null,
      tenantId: body.get('tenantId')?.toString() || null,
      totalDocuments: 1,
      totalSections: 0,
      importedCount: 0,
      duplicateCount: 0,
      failedCount: 0,
      retryCount: 0,
      errorMessage: null,
      createdAt: now,
      startedAt: now,
      finishedAt: null,
      updatedAt: now,
    }
    mockDocumentTasks.unshift(task)
    setTimeout(() => Object.assign(task, {
      status: 'COMPLETED',
      totalSections: 6,
      importedCount: 6,
      finishedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }), 1_800)
    return HttpResponse.json(task, { status: 202 })
  }),
  http.get('*/api/documents/tasks', () => HttpResponse.json(mockDocumentTasks)),
  http.get('*/api/documents/tasks/:taskId', ({ params }) =>
    responseOr404(mockDocumentTasks.find(item => item.taskId === params.taskId))),
  http.get('*/api/documents/tasks/:taskId/knowledge', ({ params }) => {
    const task = mockDocumentTasks.find(item => item.taskId === params.taskId)
    if (!task) return problem(404, 'Not found', 'document import task not found')
    return HttpResponse.json({
      task,
      items: mockKnowledgeReferences[String(params.taskId)] ?? [],
      totalCount: mockKnowledgeReferences[String(params.taskId)]?.length ?? 0,
    })
  }),
  http.post('*/api/documents/tasks/:taskId/retry', ({ params }) => {
    const task = mockDocumentTasks.find(item => item.taskId === params.taskId)
    if (!task) return problem(404, 'Not found', 'document import task not found')
    Object.assign(task, {
      status: 'PROCESSING',
      retryCount: (task.retryCount ?? 0) + 1,
      errorMessage: null,
      updatedAt: new Date().toISOString(),
    })
    return HttpResponse.json(task)
  }),
  http.post('*/trust-rag/admin/knowledge/import', async () => {
    await delay(180)
    return HttpResponse.json({
      importedCount: 1,
      duplicateCount: 0,
      failedCount: 0,
      knowledgeIds: [9001],
    }, { status: 201 })
  }),
  http.get('*/trust-rag/admin/knowledge/candidates', ({ request }) => {
    const search = new URL(request.url).searchParams
    const status = search.get('status') || 'HUMAN_REVIEW_PENDING'
    const trustLevel = search.get('trustLevel') || 'MEDIUM'
    return HttpResponse.json(mockReviewCandidates.filter(item =>
      item.status === status && item.trustLevel === trustLevel))
  }),
  http.post('*/trust-rag/admin/knowledge/:id/approve-high', async ({ params, request }) => {
    const item = mockReviewCandidates.find(candidate => candidate.id === Number(params.id))
    if (!item) return problem(404, 'Not found', 'knowledge item not found')
    const body = await request.json() as ReviewPayload
    Object.assign(item, {
      trustLevel: 'HIGH',
      status: 'HIGH_ENABLED',
      approvedBy: body.reviewerId,
      approvedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    })
    return HttpResponse.json(item)
  }),
  http.post('*/trust-rag/admin/knowledge/:id/reject', async ({ params, request }) => {
    const item = mockReviewCandidates.find(candidate => candidate.id === Number(params.id))
    if (!item) return problem(404, 'Not found', 'knowledge item not found')
    const body = await request.json() as ReviewPayload
    Object.assign(item, {
      status: 'REJECTED',
      rejectReason: body.comment ?? null,
      updatedAt: new Date().toISOString(),
    })
    return HttpResponse.json(item)
  }),
  http.get('*/trust-rag/admin/eval/datasets', ({ request }) => {
    const includeDisabled = new URL(request.url).searchParams.get('includeDisabled') === 'true'
    return HttpResponse.json(mockDatasets.filter(item => includeDisabled || item.enabled))
  }),
  http.get('*/trust-rag/admin/eval/datasets/:id', ({ params }) => responseOr404(mockDatasets.find(item => item.id === Number(params.id)))),
  http.post('*/trust-rag/admin/eval/datasets', async ({ request }) => {
    const body = await request.json() as Partial<EvalDataset>
    const item: EvalDataset = { id: nextDatasetId++, name: body.name || '未命名测试集', description: body.description ?? null, tenantId: body.tenantId ?? null, projectId: body.projectId ?? null, createdBy: body.createdBy ?? null, enabled: body.enabled ?? true, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    mockDatasets.unshift(item)
    return HttpResponse.json(item, { status: 201 })
  }),
  http.put('*/trust-rag/admin/eval/datasets/:id', async ({ params, request }) => {
    const index = mockDatasets.findIndex(item => item.id === Number(params.id))
    if (index < 0) return problem(404, 'Not found', 'eval dataset not found')
    const body = await request.json() as Partial<EvalDataset>
    mockDatasets[index] = { ...mockDatasets[index], ...body, id: mockDatasets[index].id, updatedAt: new Date().toISOString() }
    return HttpResponse.json(mockDatasets[index])
  }),
  http.delete('*/trust-rag/admin/eval/datasets/:id', ({ params }) => {
    const id = Number(params.id)
    if (mockRuns.some(run => run.datasetId === id)) return problem(409, 'TrustRAG evaluation conflict', 'eval dataset has historical runs and cannot be deleted')
    const index = mockDatasets.findIndex(item => item.id === id)
    if (index >= 0) mockDatasets.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('*/trust-rag/admin/eval/cases', ({ request }) => {
    const datasetId = Number(new URL(request.url).searchParams.get('datasetId'))
    return HttpResponse.json(mockCases.filter(item => item.datasetId === datasetId))
  }),
  http.get('*/trust-rag/admin/eval/cases/:id/expected-knowledge', ({ params }) => HttpResponse.json(mockExpected[Number(params.id)] ?? [])),
  http.get('*/trust-rag/admin/eval/cases/:id', ({ params }) => responseOr404(mockCases.find(item => item.id === Number(params.id)))),
  http.post('*/trust-rag/admin/eval/cases', async ({ request }) => {
    const body = await request.json() as Partial<EvalCase> & { datasetId: number; question: string }
    const item: EvalCase = { id: nextCaseId++, datasetId: body.datasetId, question: body.question, expectedAnswer: body.expectedAnswer ?? null, tenantId: body.tenantId ?? null, projectId: body.projectId ?? null, userId: body.userId ?? null, conversationId: body.conversationId ?? null, tags: body.tags ?? [], difficulty: body.difficulty ?? null, enabled: body.enabled ?? true, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    mockCases.push(item)
    return HttpResponse.json(item, { status: 201 })
  }),
  http.put('*/trust-rag/admin/eval/cases/:id', async ({ params, request }) => {
    const index = mockCases.findIndex(item => item.id === Number(params.id))
    if (index < 0) return problem(404, 'Not found', 'eval case not found')
    const body = await request.json() as Partial<EvalCase>
    mockCases[index] = { ...mockCases[index], ...body, id: mockCases[index].id, updatedAt: new Date().toISOString() }
    return HttpResponse.json(mockCases[index])
  }),
  http.delete('*/trust-rag/admin/eval/cases/:id', ({ params }) => {
    const id = Number(params.id)
    if (mockResults.some(result => result.evalCaseId === id)) return problem(409, 'TrustRAG evaluation conflict', 'eval case has historical results and cannot be deleted')
    const index = mockCases.findIndex(item => item.id === id)
    if (index >= 0) mockCases.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('*/trust-rag/admin/eval/cases/import-csv', async () => {
    await delay(250)
    return HttpResponse.json({ totalRows: 3, importedRows: 2, failedRows: 1, errors: [{ rowNumber: 4, message: 'expected_knowledge_ids contains invalid ID: abc' }] })
  }),
  http.get('*/trust-rag/admin/eval/runs', ({ request }) => {
    const datasetId = Number(new URL(request.url).searchParams.get('datasetId'))
    return HttpResponse.json(datasetId ? mockRuns.filter(item => item.datasetId === datasetId) : mockRuns)
  }),
  http.get('*/trust-rag/admin/eval/runs/:id', ({ params }) => responseOr404(mockRuns.find(item => item.id === Number(params.id)))),
  http.post('*/trust-rag/admin/eval/runs', async ({ request }) => {
    const body = await request.json() as Partial<EvalRun> & { datasetId: number }
    const item: EvalRun = { id: nextRunId++, datasetId: body.datasetId, runName: body.runName ?? '新评估任务', runType: body.runType ?? 'MANUAL', beforeAfterGroup: body.beforeAfterGroup ?? 'NORMAL', status: 'PENDING', totalCount: mockCases.filter(item => item.datasetId === body.datasetId).length, successCount: 0, failedCount: 0, engineConfigSnapshot: null, modelConfigSnapshot: null, knowledgeSnapshotTime: new Date().toISOString(), startedAt: null, finishedAt: null, errorMessage: null, createdBy: body.createdBy ?? null, createdAt: new Date().toISOString() }
    mockRuns.unshift(item)
    setTimeout(() => Object.assign(item, { status: 'SUCCEEDED', startedAt: item.createdAt, finishedAt: new Date().toISOString(), successCount: item.totalCount }), 3_500)
    return HttpResponse.json(item, { status: 201 })
  }),
  http.post('*/trust-rag/admin/eval/runs/:id/cancel', ({ params }) => {
    const run = mockRuns.find(item => item.id === Number(params.id))
    if (run) run.status = 'CANCELED'
    return new HttpResponse(null, { status: 200 })
  }),
  http.get('*/trust-rag/admin/eval/runs/:id/results', ({ params }) => HttpResponse.json(mockResults.filter(item => item.evalRunId === Number(params.id)))),
  http.get('*/trust-rag/admin/eval/runs/:id/report', ({ params }) => responseOr404(mockReports[Number(params.id)] ?? mockReports[100])),
  http.get('*/trust-rag/admin/eval/results/:id/judge-details', ({ params }) => HttpResponse.json(mockJudgeDetails.filter(item => item.evalResultId === Number(params.id)))),
  http.post('*/trust-rag/admin/eval/compare', async ({ request }) => {
    const body = await request.json() as { beforeRunId: number; afterRunId: number }
    return HttpResponse.json({ id: 1, ...body, recallAt10Delta: .084, mrrDelta: .084, faithfulnessDelta: .084, answerCorrectnessDelta: .084, hallucinationScoreDelta: -.084, conclusion: 'after run improved overall', createdAt: new Date().toISOString() })
  }),
  http.get('*/trust-rag/admin/eval/governance/summary', () => HttpResponse.json(mockGovernance.at(-1))),
  http.get('*/trust-rag/admin/eval/governance/trend', () => HttpResponse.json(mockGovernance)),
  http.post('*/trust-rag/admin/eval/governance/snapshot', () => HttpResponse.json(mockGovernance.at(-1))),
]

function responseOr404<T>(value: T | undefined) {
  return value ? HttpResponse.json(value) : problem(404, 'Not found', 'evaluation resource not found')
}

function problem(status: number, title: string, detail: string) {
  return HttpResponse.json({ status, title, detail }, { status })
}
