/**
 * 评估管理台 API 实例装配，统一创建 HTTP 客户端和领域 API。
 */
import { createEvaluationApi } from './evaluation'
import { createHttpClient } from './http'
import { createInfrastructureApi } from './infrastructure'
import { createKnowledgeApi } from './knowledge'

const defaultBaseUrl = import.meta.env.VITE_API_BASE_URL || '/trust-rag/admin/eval'
const defaultAdminBaseUrl = import.meta.env.VITE_ADMIN_API_BASE_URL || ''

export const httpClient = createHttpClient({
  baseURL: localStorage.getItem('trust-rag.api-base-url') || defaultBaseUrl,
  getToken: () => localStorage.getItem('trust-rag.token'),
})

export const adminHttpClient = createHttpClient({
  baseURL: localStorage.getItem('trust-rag.admin-api-base-url') || defaultAdminBaseUrl,
  getToken: () => localStorage.getItem('trust-rag.token'),
})

export const evaluationApi = createEvaluationApi(httpClient)
export const knowledgeApi = createKnowledgeApi(adminHttpClient)
export const infrastructureApi = createInfrastructureApi(adminHttpClient)
