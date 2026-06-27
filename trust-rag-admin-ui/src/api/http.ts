/**
 * HTTP 客户端基础设施，统一处理鉴权、参数清理和错误归一化。
 */
import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

export interface HttpClientOptions {
  baseURL: string
  getToken: () => string | null
  timeoutMs?: number
}

interface ProblemDetailBody {
  title?: string
  detail?: string
  status?: number
  errors?: Record<string, string | string[]>
}

export class ApiError extends Error {
  readonly status: number | null
  readonly title: string
  readonly detail: string
  readonly fieldErrors: Record<string, string[]>

  constructor(
    status: number | null,
    title: string,
    detail: string,
    fieldErrors: Record<string, string[]> = {},
  ) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
  }
}

/**
 * 创建管理台 HTTP 客户端。
 *
 * 请求拦截器负责清理空查询参数和注入 Bearer Token；
 * 响应拦截器把后端 Problem Detail 统一转换为 ApiError。
 */
export function createHttpClient(options: HttpClientOptions): AxiosInstance {
  const client = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeoutMs ?? 20_000,
  })

  client.interceptors.request.use((config) => prepareRequest(config, options.getToken))
  client.interceptors.response.use(
    (response) => response,
    (reason: unknown) => Promise.reject(normalizeApiError(reason)),
  )
  return client
}

function prepareRequest(
  config: InternalAxiosRequestConfig,
  getToken: () => string | null,
): InternalAxiosRequestConfig {
  if (config.params && typeof config.params === 'object' && !Array.isArray(config.params)) {
    // 清理 null/undefined，避免后端把空字符串和未传参数混在一起处理。
    config.params = Object.fromEntries(
      Object.entries(config.params as Record<string, unknown>)
        .filter(([, value]) => value !== null && value !== undefined),
    )
  }
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
}

export function normalizeApiError(reason: unknown): ApiError {
  if (reason instanceof ApiError) {
    return reason
  }
  if (!axios.isAxiosError(reason)) {
    const detail = reason instanceof Error ? reason.message : '未知请求错误'
    return new ApiError(null, '请求失败', detail)
  }
  const error = reason as AxiosError<ProblemDetailBody>
  const body = error.response?.data
  const status = error.response?.status ?? null
  // 后端如果按 RFC 7807 返回 detail/title，前端优先展示服务端给出的业务原因。
  const detail = body?.detail
    ?? (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : error.message)
  return new ApiError(
    status,
    body?.title ?? (status ? `请求失败 (${status})` : '无法连接评估服务'),
    detail,
    normalizeFieldErrors(body?.errors),
  )
}

function normalizeFieldErrors(
  errors: Record<string, string | string[]> | undefined,
): Record<string, string[]> {
  if (!errors) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(errors).map(([field, messages]) => [
      field,
      Array.isArray(messages) ? messages : [messages],
    ]),
  )
}
