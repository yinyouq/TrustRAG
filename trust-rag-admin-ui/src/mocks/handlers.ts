import { delay, http, HttpResponse } from 'msw'
import type { EvalCase, EvalDataset, EvalRun } from '@/api/types'
import { mockCases, mockDatasets, mockExpected, mockGovernance, mockJudgeDetails, mockReports, mockResults, mockRuns } from './fixtures'

let nextDatasetId = 20
let nextCaseId = 50
let nextRunId = 200

export const handlers = [
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
