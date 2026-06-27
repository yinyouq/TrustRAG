/**
 * 全局应用状态，保存当前作用域和访问令牌。
 */
import { defineStore } from 'pinia'

const STORAGE_PREFIX = 'trust-rag.'

/**
 * 全局应用状态。
 *
 * tenant/project/token 会持久化到 localStorage，方便管理台刷新后继续沿用同一作用域。
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    tenantId: localStorage.getItem(`${STORAGE_PREFIX}tenant-id`) || '',
    projectId: localStorage.getItem(`${STORAGE_PREFIX}project-id`) || '',
    token: localStorage.getItem(`${STORAGE_PREFIX}token`) || '',
  }),
  actions: {
    setScope(tenantId: string, projectId: string) {
      this.tenantId = tenantId.trim()
      this.projectId = projectId.trim()
      persist(`${STORAGE_PREFIX}tenant-id`, this.tenantId)
      persist(`${STORAGE_PREFIX}project-id`, this.projectId)
    },
    setToken(token: string) {
      this.token = token.trim()
      persist(`${STORAGE_PREFIX}token`, this.token)
    },
  },
})

function persist(key: string, value: string) {
  if (value) {
    localStorage.setItem(key, value)
  } else {
    localStorage.removeItem(key)
  }
}
