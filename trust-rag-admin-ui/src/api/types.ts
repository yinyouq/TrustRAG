export type EvalRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'
export type EvalRunType = 'MANUAL' | 'SCHEDULED' | 'BEFORE_AFTER' | 'REGRESSION'
export type BeforeAfterGroup = 'NORMAL' | 'BEFORE' | 'AFTER'
export type EvalResultStatus = 'SUCCEEDED' | 'FAILED'
export type JudgeType = 'FAITHFULNESS' | 'ANSWER_CORRECTNESS' | 'ANSWER_RELEVANCE'

export interface EvalDataset {
  id: number
  name: string
  description: string | null
  tenantId: string | null
  projectId: string | null
  createdBy: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface EvalDatasetPayload {
  name: string
  description?: string | null
  tenantId?: string | null
  projectId?: string | null
  createdBy?: string | null
  enabled?: boolean
}

export interface EvalCase {
  id: number
  datasetId: number
  question: string
  expectedAnswer: string | null
  tenantId: string | null
  projectId: string | null
  userId: string | null
  conversationId: string | null
  tags: string[]
  difficulty: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ExpectedKnowledge {
  id: number | null
  evalCaseId: number | null
  knowledgeId: number
  relevanceGrade: number
  createdAt: string | null
}

export interface ExpectedKnowledgePayload {
  knowledgeId: number
  relevanceGrade: number
}

export interface EvalCasePayload {
  datasetId: number
  question: string
  expectedAnswer?: string | null
  tenantId?: string | null
  projectId?: string | null
  userId?: string | null
  conversationId?: string | null
  tags?: string[]
  difficulty?: string | null
  enabled?: boolean
  expectedKnowledge?: ExpectedKnowledgePayload[]
}

export interface EvalRun {
  id: number
  datasetId: number
  runName: string | null
  runType: EvalRunType
  beforeAfterGroup: BeforeAfterGroup
  status: EvalRunStatus
  totalCount: number
  successCount: number
  failedCount: number
  engineConfigSnapshot: string | null
  modelConfigSnapshot: string | null
  knowledgeSnapshotTime: string | null
  startedAt: string | null
  finishedAt: string | null
  errorMessage: string | null
  createdBy: string | null
  createdAt: string
}

export interface EvalRunPayload {
  datasetId: number
  runName?: string | null
  runType?: EvalRunType
  beforeAfterGroup?: BeforeAfterGroup
  engineConfigSnapshot?: string | null
  modelConfigSnapshot?: string | null
  createdBy?: string | null
  async?: boolean
}

export interface EvalResult {
  id: number
  evalRunId: number
  evalCaseId: number
  traceId: string | null
  question: string
  expectedAnswer: string | null
  answer: string | null
  retrievedCount: number
  expectedKnowledgeCount: number
  recallAt5: number | null
  recallAt10: number | null
  precisionAt5: number | null
  precisionAt10: number | null
  mrr: number | null
  ndcgAt5: number | null
  ndcgAt10: number | null
  faithfulness: number | null
  answerCorrectness: number | null
  answerRelevance: number | null
  hallucinationScore: number | null
  promptTokens: number
  completionTokens: number
  latencyMs: number
  status: EvalResultStatus
  errorMessage: string | null
  createdAt: string
}

export interface EvalJudgeDetail {
  id: number
  evalResultId: number
  evalRunId: number
  evalCaseId: number
  judgeType: JudgeType
  model: string | null
  prompt: string | null
  rawOutput: string | null
  score: number | null
  passed: boolean | null
  reason: string | null
  createdAt: string
}

export interface EvalReport {
  id: number
  evalRunId: number
  datasetId: number
  totalCount: number
  successCount: number
  failedCount: number
  avgRecallAt5: number | null
  avgRecallAt10: number | null
  avgPrecisionAt5: number | null
  avgPrecisionAt10: number | null
  avgMrr: number | null
  avgNdcgAt5: number | null
  avgNdcgAt10: number | null
  avgFaithfulness: number | null
  avgAnswerCorrectness: number | null
  avgAnswerRelevance: number | null
  avgHallucinationScore: number | null
  avgLatencyMs: number | null
  p90LatencyMs: number | null
  summaryJson: string | null
  createdAt: string
}

export interface EvalCompareReport {
  id: number
  beforeRunId: number
  afterRunId: number
  recallAt10Delta: number | null
  mrrDelta: number | null
  faithfulnessDelta: number | null
  answerCorrectnessDelta: number | null
  hallucinationScoreDelta: number | null
  conclusion: string | null
  createdAt: string
}

export interface EvalGovernanceSnapshot {
  id: number
  tenantId: string | null
  projectId: string | null
  snapshotDate: string
  totalCandidateCount: number
  approvedCandidateCount: number
  rejectedCandidateCount: number
  candidateApprovalRate: number | null
  knowledgeReuseRate: number | null
  contaminationRate: number | null
  privacyLeakageRate: number | null
  gapResolveRate: number | null
  createdAt: string
}

export interface EvalCaseCsvImportResult {
  totalRows: number
  importedRows: number
  failedRows: number
  errors: Array<{ rowNumber: number; message: string }>
}
