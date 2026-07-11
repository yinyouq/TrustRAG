/**
 * 知识库管理 API 门面，封装文档导入任务和手动知识导入接口。
 */
import type { AxiosInstance } from 'axios'

export type TrustLevel = 'HIGH' | 'MEDIUM' | 'LOW'
export type ScopeType = 'GLOBAL' | 'TENANT' | 'PROJECT' | 'USER' | 'CONVERSATION' | 'GLOBAL_CANDIDATE'
export type DocumentImportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
export type KnowledgeStatus =
  | 'LOW_PENDING'
  | 'LOW_ENABLED'
  | 'PROMOTION_PENDING'
  | 'PROMOTION_RUNNING'
  | 'MEDIUM_ENABLED'
  | 'HUMAN_REVIEW_PENDING'
  | 'HIGH_ENABLED'
  | 'REJECTED'
  | 'CONFLICT'
  | 'EXPIRED'
  | 'MERGE_PENDING'
  | 'INDEXING'
  | 'INDEX_FAILED'
  | 'ROLLBACK'

export interface DocumentUploadPayload {
  file: File
  title?: string | null
  sourceUrl?: string | null
  sourceType?: string | null
  trustLevel?: TrustLevel | null
  scopeType?: ScopeType | null
  userId?: string | null
  conversationId?: string | null
  projectId?: string | null
  tenantId?: string | null
}

export interface DocumentImportTask {
  taskId: string
  sourceKind?: 'UPLOAD' | 'GIT'
  status: DocumentImportStatus
  title?: string | null
  originalFilename?: string | null
  sourceUri?: string | null
  sourceType?: string | null
  trustLevel?: TrustLevel | null
  scopeType?: ScopeType | null
  userId?: string | null
  conversationId?: string | null
  projectId?: string | null
  tenantId?: string | null
  totalDocuments?: number
  totalSections?: number
  importedCount?: number
  duplicateCount?: number
  failedCount?: number
  retryCount?: number
  errorMessage?: string | null
  createdAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  updatedAt?: string | null
}

export interface KnowledgeImportPayload {
  title: string
  content: string
  sourceType?: string | null
  sourceRef?: string | null
  trustLevel?: TrustLevel | null
  scopeType?: ScopeType | null
  userId?: string | null
  conversationId?: string | null
  projectId?: string | null
  tenantId?: string | null
}

export interface KnowledgeImportResult {
  importedCount: number
  duplicateCount: number
  failedCount: number
  knowledgeIds: number[]
}

export interface KnowledgeItem {
  id: number
  title: string
  claim?: string | null
  content: string
  summary?: string | null
  knowledgeType?: string | null
  trustLevel: TrustLevel
  status: KnowledgeStatus
  scopeType: ScopeType
  userId?: string | null
  conversationId?: string | null
  projectId?: string | null
  tenantId?: string | null
  sourceType?: string | null
  sourceRef?: string | null
  evidence?: string | null
  confidence?: number | null
  privacyScore?: number | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  expiresAt?: string | null
}

export interface KnowledgeReferenceItem {
  knowledgeId: number
  expectedKnowledgeIds: string
  title?: string | null
  contentPreview: string
  content: string
  scopeType?: ScopeType | null
  tenantId?: string | null
  projectId?: string | null
  userId?: string | null
  conversationId?: string | null
  trustLevel?: TrustLevel | null
  status: KnowledgeStatus
  sourceType?: string | null
  sourceRef?: string | null
  sourceTitle?: string | null
  sourceUrl?: string | null
  pageNumber?: number | null
  sectionPath?: string | null
  documentId?: string | null
  chunkIndex?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface DocumentKnowledgeResponse {
  task: DocumentImportTask
  items: KnowledgeReferenceItem[]
  totalCount: number
}

export interface DocumentTaskFilters {
  limit?: number
  offset?: number
}

export interface ReviewCandidateFilters {
  status?: KnowledgeStatus | null
  trustLevel?: TrustLevel | null
  scopeType?: ScopeType | null
  limit?: number
  offset?: number
}

export interface KnowledgeFilters {
  trustLevel?: TrustLevel | null
  status?: KnowledgeStatus | null
  limit?: number
  offset?: number
}

export interface ReviewPayload {
  reviewerId: string
  comment?: string | null
  modifiedTitle?: string | null
  modifiedContent?: string | null
}

export interface LifecyclePayload {
  operatorId: string
  reason: string
}

export interface MergeKnowledgePayload {
  operatorId: string
  targetKnowledgeId: number
  sourceKnowledgeIds: number[]
}

export function createKnowledgeApi(client: AxiosInstance) {
  return {
    async uploadDocument(payload: DocumentUploadPayload) {
      const body = new FormData()
      body.append('file', payload.file)
      appendOptional(body, 'title', payload.title)
      appendOptional(body, 'sourceUrl', payload.sourceUrl)
      appendOptional(body, 'sourceType', payload.sourceType)
      appendOptional(body, 'trustLevel', payload.trustLevel)
      appendOptional(body, 'scopeType', payload.scopeType)
      appendOptional(body, 'userId', payload.userId)
      appendOptional(body, 'conversationId', payload.conversationId)
      appendOptional(body, 'projectId', payload.projectId)
      appendOptional(body, 'tenantId', payload.tenantId)
      return (await client.post<DocumentImportTask>('/api/documents/upload', body)).data
    },
    async getDocumentTask(taskId: string) {
      return (await client.get<DocumentImportTask>(`/api/documents/tasks/${encodeURIComponent(taskId)}`)).data
    },
    async retryDocumentTask(taskId: string) {
      return (await client.post<DocumentImportTask>(`/api/documents/tasks/${encodeURIComponent(taskId)}/retry`)).data
    },
    async listDocumentTasks(filters: DocumentTaskFilters = {}) {
      return (await client.get<DocumentImportTask[]>('/api/documents/tasks', {
        params: {
          limit: filters.limit ?? 100,
          offset: filters.offset ?? 0,
        },
      })).data
    },
    async listDocumentKnowledge(taskId: string, filters: DocumentTaskFilters = {}) {
      return (await client.get<DocumentKnowledgeResponse>(
        `/api/documents/tasks/${encodeURIComponent(taskId)}/knowledge`,
        {
          params: {
            limit: filters.limit ?? 1000,
            offset: filters.offset ?? 0,
          },
        },
      )).data
    },
    async importKnowledge(payload: KnowledgeImportPayload) {
      return (await client.post<KnowledgeImportResult>('/trust-rag/admin/knowledge/import', payload)).data
    },
    async listReviewCandidates(filters: ReviewCandidateFilters = {}) {
      return (await client.get<KnowledgeItem[]>('/trust-rag/admin/knowledge/candidates', {
        params: {
          status: filters.status ?? 'HUMAN_REVIEW_PENDING',
          trustLevel: filters.trustLevel ?? 'MEDIUM',
          scopeType: filters.scopeType,
          limit: filters.limit ?? 50,
          offset: filters.offset ?? 0,
        },
      })).data
    },
    async listKnowledge(filters: KnowledgeFilters = {}) {
      return (await client.get<KnowledgeItem[]>('/trust-rag/admin/knowledge', {
        params: {
          trustLevel: filters.trustLevel,
          status: filters.status,
          limit: filters.limit ?? 50,
          offset: filters.offset ?? 0,
        },
      })).data
    },
    async getKnowledge(id: number) {
      return (await client.get<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}`,
      )).data
    },
    async approveHigh(id: number, payload: ReviewPayload) {
      return (await client.post<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}/approve-high`,
        payload,
      )).data
    },
    async rejectCandidate(id: number, payload: ReviewPayload) {
      return (await client.post<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}/reject`,
        payload,
      )).data
    },
    async downgradeKnowledge(id: number, payload: LifecyclePayload) {
      return (await client.post<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}/downgrade`,
        payload,
      )).data
    },
    async rollbackKnowledge(id: number, payload: LifecyclePayload) {
      return (await client.post<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}/rollback`,
        payload,
      )).data
    },
    async mergeKnowledge(payload: MergeKnowledgePayload) {
      return (await client.post<KnowledgeItem>(
        '/trust-rag/admin/knowledge/merge',
        payload,
      )).data
    },
    async deleteKnowledge(id: number, payload: LifecyclePayload) {
      return (await client.delete<KnowledgeItem>(
        `/trust-rag/admin/knowledge/${id}`,
        { data: payload },
      )).data
    },
  }
}

function appendOptional(body: FormData, name: string, value: string | null | undefined) {
  if (value !== null && value !== undefined && value !== '') {
    body.append(name, value)
  }
}

export type KnowledgeApi = ReturnType<typeof createKnowledgeApi>
