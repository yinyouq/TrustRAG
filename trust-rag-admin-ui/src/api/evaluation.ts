import type { AxiosInstance } from 'axios'
import type {
  EvalCase,
  EvalCaseCsvImportResult,
  EvalCasePayload,
  EvalCompareReport,
  EvalDataset,
  EvalDatasetPayload,
  EvalGovernanceSnapshot,
  EvalJudgeDetail,
  EvalReport,
  EvalResult,
  EvalRun,
  EvalRunPayload,
  ExpectedKnowledge,
  ExpectedKnowledgePayload,
} from './types'

export interface DatasetFilters {
  tenantId?: string | null
  projectId?: string | null
  includeDisabled?: boolean
  limit?: number
  offset?: number
}

export interface CaseFilters {
  onlyEnabled?: boolean
  limit?: number
  offset?: number
}

export interface ScopeFilters {
  tenantId?: string | null
  projectId?: string | null
}

export function createEvaluationApi(client: AxiosInstance) {
  return {
    async listDatasets(filters: DatasetFilters = {}) {
      return (await client.get<EvalDataset[]>('/datasets', { params: filters })).data
    },
    async getDataset(id: number) {
      return (await client.get<EvalDataset>(`/datasets/${id}`)).data
    },
    async createDataset(payload: EvalDatasetPayload) {
      return (await client.post<EvalDataset>('/datasets', payload)).data
    },
    async updateDataset(id: number, payload: EvalDatasetPayload) {
      return (await client.put<EvalDataset>(`/datasets/${id}`, payload)).data
    },
    async deleteDataset(id: number) {
      await client.delete(`/datasets/${id}`)
    },
    async listCases(datasetId: number, filters: CaseFilters = {}) {
      return (await client.get<EvalCase[]>('/cases', {
        params: { datasetId, ...filters },
      })).data
    },
    async getCase(id: number) {
      return (await client.get<EvalCase>(`/cases/${id}`)).data
    },
    async createCase(payload: EvalCasePayload) {
      return (await client.post<EvalCase>('/cases', payload)).data
    },
    async updateCase(id: number, payload: EvalCasePayload) {
      return (await client.put<EvalCase>(`/cases/${id}`, payload)).data
    },
    async deleteCase(id: number) {
      await client.delete(`/cases/${id}`)
    },
    async getExpectedKnowledge(caseId: number) {
      return (await client.get<ExpectedKnowledge[]>(`/cases/${caseId}/expected-knowledge`)).data
    },
    async replaceExpectedKnowledge(caseId: number, values: ExpectedKnowledgePayload[]) {
      await client.put(`/cases/${caseId}/expected-knowledge`, { expectedKnowledge: values })
    },
    async importCases(
      datasetId: number,
      file: File,
      defaults: ScopeFilters & { userId?: string; conversationId?: string } = {},
    ) {
      const body = new FormData()
      body.append('file', file)
      return (await client.post<EvalCaseCsvImportResult>('/cases/import-csv', body, {
        params: { datasetId, ...defaults },
      })).data
    },
    async listRuns(datasetId?: number | null, limit = 100) {
      return (await client.get<EvalRun[]>('/runs', { params: { datasetId, limit } })).data
    },
    async getRun(id: number) {
      return (await client.get<EvalRun>(`/runs/${id}`)).data
    },
    async createRun(payload: EvalRunPayload) {
      return (await client.post<EvalRun>('/runs', payload)).data
    },
    async cancelRun(id: number) {
      await client.post(`/runs/${id}/cancel`)
    },
    async listResults(runId: number, limit = 500) {
      return (await client.get<EvalResult[]>(`/runs/${runId}/results`, { params: { limit } })).data
    },
    async getReport(runId: number) {
      return (await client.get<EvalReport>(`/runs/${runId}/report`)).data
    },
    async listJudgeDetails(resultId: number) {
      return (await client.get<EvalJudgeDetail[]>(`/results/${resultId}/judge-details`)).data
    },
    async compareRuns(beforeRunId: number, afterRunId: number) {
      return (await client.post<EvalCompareReport>('/compare', { beforeRunId, afterRunId })).data
    },
    async getGovernanceSummary(filters: ScopeFilters = {}) {
      return (await client.get<EvalGovernanceSnapshot>('/governance/summary', { params: filters })).data
    },
    async getGovernanceTrend(
      filters: ScopeFilters & { from?: string | null; to?: string | null } = {},
    ) {
      return (await client.get<EvalGovernanceSnapshot[]>('/governance/trend', { params: filters })).data
    },
    async captureGovernanceSnapshot(payload: ScopeFilters & { snapshotDate?: string | null } = {}) {
      return (await client.post<EvalGovernanceSnapshot>('/governance/snapshot', payload)).data
    },
  }
}

export type EvaluationApi = ReturnType<typeof createEvaluationApi>
