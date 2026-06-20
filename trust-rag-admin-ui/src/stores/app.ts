import { defineStore } from 'pinia'

const STORAGE_PREFIX = 'trust-rag.'

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
