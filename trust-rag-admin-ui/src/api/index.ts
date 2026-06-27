/**
 * 评估管理台 API 实例装配，统一创建 HTTP 客户端和领域 API。
 */
import { createEvaluationApi } from './evaluation'
import { createHttpClient } from './http'

const defaultBaseUrl = import.meta.env.VITE_API_BASE_URL || '/trust-rag/admin/eval'

export const httpClient = createHttpClient({
  baseURL: localStorage.getItem('trust-rag.api-base-url') || defaultBaseUrl,
  getToken: () => localStorage.getItem('trust-rag.token'),
})

export const evaluationApi = createEvaluationApi(httpClient)
