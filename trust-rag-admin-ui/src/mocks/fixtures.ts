/**
 * Mock 数据夹具，提供评估台本地开发和测试使用的样例数据。
 */
import type {
  EvalCase,
  EvalDataset,
  EvalGovernanceSnapshot,
  EvalJudgeDetail,
  EvalReport,
  EvalResult,
  EvalRun,
  ExpectedKnowledge,
} from '@/api/types'
import type { DocumentImportTask, KnowledgeItem, KnowledgeReferenceItem } from '@/api/knowledge'

const now = '2026-06-20T12:00:00Z'

export const mockDatasets: EvalDataset[] = [
  { id: 1, name: 'Hybrid Search 回归集', description: '验证 RRF、Rerank 与 Query Rewrite 的综合收益', tenantId: 'demo', projectId: 'trust-rag', createdBy: 'admin', enabled: true, createdAt: now, updatedAt: now },
  { id: 2, name: '知识补全效果集', description: '验证高可信知识入库前后的回答变化', tenantId: 'demo', projectId: 'trust-rag', createdBy: 'admin', enabled: true, createdAt: now, updatedAt: now },
]

export const mockCases: EvalCase[] = [
  { id: 11, datasetId: 1, question: '系统如何判断存在知识缺口？', expectedAnswer: '综合检索为空、低分、低可信知识和不确定回答判断。', tenantId: 'demo', projectId: 'trust-rag', userId: null, conversationId: null, tags: ['gap', 'governance'], difficulty: 'MEDIUM', enabled: true, createdAt: now, updatedAt: now },
  { id: 12, datasetId: 1, question: '混合检索怎样融合 Milvus 和 OpenSearch？', expectedAnswer: '通过 RRF 融合向量与关键词排序结果。', tenantId: 'demo', projectId: 'trust-rag', userId: null, conversationId: null, tags: ['retrieval', 'rrf'], difficulty: 'HARD', enabled: true, createdAt: now, updatedAt: now },
  { id: 21, datasetId: 2, question: '候选知识何时进入高可信池？', expectedAnswer: '经过证据、隐私、冲突和质量门槛后晋升。', tenantId: 'demo', projectId: 'trust-rag', userId: null, conversationId: null, tags: ['promotion'], difficulty: 'MEDIUM', enabled: true, createdAt: now, updatedAt: now },
]

export const mockExpected: Record<number, ExpectedKnowledge[]> = {
  11: [{ id: 1, evalCaseId: 11, knowledgeId: 101, relevanceGrade: 3, createdAt: now }],
  12: [{ id: 2, evalCaseId: 12, knowledgeId: 102, relevanceGrade: 3, createdAt: now }],
  21: [{ id: 3, evalCaseId: 21, knowledgeId: 201, relevanceGrade: 3, createdAt: now }],
}

export const mockRuns: EvalRun[] = [
  { id: 103, datasetId: 1, runName: 'Nightly regression', runType: 'REGRESSION', beforeAfterGroup: 'NORMAL', status: 'RUNNING', totalCount: 24, successCount: 14, failedCount: 1, engineConfigSnapshot: '{}', modelConfigSnapshot: '{}', knowledgeSnapshotTime: now, startedAt: '2026-06-20T11:58:00Z', finishedAt: null, errorMessage: null, createdBy: 'scheduler', createdAt: '2026-06-20T11:58:00Z' },
  { id: 102, datasetId: 2, runName: '知识补全后', runType: 'BEFORE_AFTER', beforeAfterGroup: 'AFTER', status: 'SUCCEEDED', totalCount: 20, successCount: 19, failedCount: 1, engineConfigSnapshot: '{}', modelConfigSnapshot: '{}', knowledgeSnapshotTime: now, startedAt: '2026-06-20T10:10:00Z', finishedAt: '2026-06-20T10:11:20Z', errorMessage: null, createdBy: 'admin', createdAt: '2026-06-20T10:10:00Z' },
  { id: 101, datasetId: 2, runName: '知识补全前', runType: 'BEFORE_AFTER', beforeAfterGroup: 'BEFORE', status: 'SUCCEEDED', totalCount: 20, successCount: 17, failedCount: 3, engineConfigSnapshot: '{}', modelConfigSnapshot: '{}', knowledgeSnapshotTime: now, startedAt: '2026-06-20T09:00:00Z', finishedAt: '2026-06-20T09:01:30Z', errorMessage: null, createdBy: 'admin', createdAt: '2026-06-20T09:00:00Z' },
  { id: 100, datasetId: 1, runName: 'Hybrid v2 基线', runType: 'MANUAL', beforeAfterGroup: 'NORMAL', status: 'SUCCEEDED', totalCount: 24, successCount: 23, failedCount: 1, engineConfigSnapshot: '{}', modelConfigSnapshot: '{}', knowledgeSnapshotTime: now, startedAt: '2026-06-19T12:00:00Z', finishedAt: '2026-06-19T12:01:10Z', errorMessage: null, createdBy: 'admin', createdAt: '2026-06-19T12:00:00Z' },
]

function report(runId: number, datasetId: number, lift = 0): EvalReport {
  return { id: runId, evalRunId: runId, datasetId, totalCount: datasetId === 1 ? 24 : 20, successCount: datasetId === 1 ? 23 : 19, failedCount: 1, avgRecallAt5: .78 + lift, avgRecallAt10: .864 + lift, avgPrecisionAt5: .61 + lift, avgPrecisionAt10: .42 + lift, avgMrr: .81 + lift, avgNdcgAt5: .8 + lift, avgNdcgAt10: .84 + lift, avgFaithfulness: .912 + lift, avgAnswerCorrectness: .86 + lift, avgAnswerRelevance: .93 + lift, avgHallucinationScore: .088 - lift, avgLatencyMs: 1250, p90LatencyMs: 1950, summaryJson: '{}', createdAt: now }
}

export const mockReports: Record<number, EvalReport> = {
  100: report(100, 1),
  101: report(101, 2, -.05),
  102: report(102, 2, .034),
}

export const mockResults: EvalResult[] = [
  { id: 1001, evalRunId: 100, evalCaseId: 11, traceId: 'eval-trace-1', question: mockCases[0].question, expectedAnswer: mockCases[0].expectedAnswer, answer: '系统会结合召回结果、分数和回答不确定性判断知识缺口。', retrievedCount: 8, expectedKnowledgeCount: 1, recallAt5: 1, recallAt10: 1, precisionAt5: .2, precisionAt10: .1, mrr: 1, ndcgAt5: 1, ndcgAt10: 1, faithfulness: .94, answerCorrectness: .9, answerRelevance: .96, hallucinationScore: .06, promptTokens: 780, completionTokens: 120, latencyMs: 1180, status: 'SUCCEEDED', errorMessage: null, createdAt: now },
  { id: 1002, evalRunId: 100, evalCaseId: 12, traceId: 'eval-trace-2', question: mockCases[1].question, expectedAnswer: mockCases[1].expectedAnswer, answer: null, retrievedCount: 0, expectedKnowledgeCount: 1, recallAt5: 0, recallAt10: 0, precisionAt5: 0, precisionAt10: 0, mrr: 0, ndcgAt5: 0, ndcgAt10: 0, faithfulness: null, answerCorrectness: null, answerRelevance: null, hallucinationScore: null, promptTokens: 0, completionTokens: 0, latencyMs: 150, status: 'FAILED', errorMessage: 'OpenSearch and Milvus unavailable', createdAt: now },
]

export const mockJudgeDetails: EvalJudgeDetail[] = [
  { id: 1, evalResultId: 1001, evalRunId: 100, evalCaseId: 11, judgeType: 'FAITHFULNESS', model: 'qwen-plus', prompt: 'Evaluate whether the answer is supported by context.', rawOutput: '{"score":0.94,"pass":true,"reason":"All claims are supported"}', score: .94, passed: true, reason: '所有关键结论均有上下文支持', createdAt: now },
  { id: 2, evalResultId: 1001, evalRunId: 100, evalCaseId: 11, judgeType: 'ANSWER_CORRECTNESS', model: 'qwen-plus', prompt: 'Compare actual answer with expected answer.', rawOutput: '{"score":0.90,"pass":true,"reason":"Core points covered"}', score: .9, passed: true, reason: '覆盖标准答案核心要点', createdAt: now },
]

export const mockGovernance: EvalGovernanceSnapshot[] = Array.from({ length: 7 }, (_, index) => ({
  id: index + 1, tenantId: 'demo', projectId: 'trust-rag', snapshotDate: `2026-06-${String(14 + index).padStart(2, '0')}`,
  totalCandidateCount: 120 + index * 4, approvedCandidateCount: 78 + index * 3, rejectedCandidateCount: 22,
  candidateApprovalRate: .74 + index * .008, knowledgeReuseRate: .58 + index * .02,
  contaminationRate: .07 - index * .005, privacyLeakageRate: index === 3 ? .01 : 0,
  gapResolveRate: .46 + index * .025, createdAt: now,
}))

export const mockDocumentTasks: DocumentImportTask[] = [
  {
    taskId: 'mock-doc-task-1',
    sourceKind: 'UPLOAD',
    status: 'COMPLETED',
    title: 'TrustRAG 指南',
    originalFilename: 'trust-rag-guide.md',
    sourceUri: null,
    sourceType: 'document',
    trustLevel: 'HIGH',
    scopeType: 'PROJECT',
    userId: null,
    conversationId: null,
    projectId: 'trust-rag',
    tenantId: 'demo',
    totalDocuments: 1,
    totalSections: 8,
    importedCount: 8,
    duplicateCount: 0,
    failedCount: 0,
    retryCount: 0,
    errorMessage: null,
    createdAt: now,
    startedAt: now,
    finishedAt: now,
    updatedAt: now,
  },
]

export const mockKnowledgeReferences: Record<string, KnowledgeReferenceItem[]> = {
  'mock-doc-task-1': [
    {
      knowledgeId: 101,
      expectedKnowledgeIds: '101',
      title: 'TrustRAG 指南 / 缺口判断',
      contentPreview: '系统会结合检索为空、低分、低可信知识和回答不确定性判断可能存在知识缺口。',
      content: '系统会结合检索为空、低分、低可信知识和回答不确定性判断可能存在知识缺口，并记录 gap 类型和原因。',
      scopeType: 'PROJECT',
      tenantId: 'demo',
      projectId: 'trust-rag',
      userId: null,
      conversationId: null,
      trustLevel: 'HIGH',
      status: 'HIGH_ENABLED',
      sourceType: 'document',
      sourceRef: 'document://mock-doc-task-1',
      sourceTitle: 'TrustRAG 指南',
      sourceUrl: null,
      pageNumber: null,
      sectionPath: '评估 / 缺口判断',
      documentId: 'mock-doc-task-1',
      chunkIndex: 0,
      createdAt: now,
      updatedAt: now,
    },
    {
      knowledgeId: 102,
      expectedKnowledgeIds: '102',
      title: 'TrustRAG 指南 / 混合检索',
      contentPreview: '混合检索通过 RRF 融合 Milvus 向量检索和 OpenSearch 关键词检索结果。',
      content: '混合检索通过 RRF 融合 Milvus 向量检索和 OpenSearch 关键词检索结果，并保留 vector_rank、keyword_rank、rrf_score 便于诊断。',
      scopeType: 'PROJECT',
      tenantId: 'demo',
      projectId: 'trust-rag',
      userId: null,
      conversationId: null,
      trustLevel: 'HIGH',
      status: 'HIGH_ENABLED',
      sourceType: 'document',
      sourceRef: 'document://mock-doc-task-1',
      sourceTitle: 'TrustRAG 指南',
      sourceUrl: null,
      pageNumber: null,
      sectionPath: '检索 / Hybrid',
      documentId: 'mock-doc-task-1',
      chunkIndex: 1,
      createdAt: now,
      updatedAt: now,
    },
  ],
}

export const mockReviewCandidates: KnowledgeItem[] = [
  {
    id: 9001,
    title: '候选知识：混合检索降级策略',
    content: '当 Milvus 或 OpenSearch 单路不可用时，TrustRAG 会自动降级为另一条可用检索链路；两路都不可用时进入无上下文回答和知识缺口流程。',
    trustLevel: 'MEDIUM',
    status: 'HUMAN_REVIEW_PENDING',
    scopeType: 'GLOBAL',
    sourceType: 'internal_doc',
    sourceRef: 'mock://review/hybrid-fallback',
    confidence: 0.86,
    privacyScore: 0,
    createdAt: now,
    updatedAt: now,
  },
]
