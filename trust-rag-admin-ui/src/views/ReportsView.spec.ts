import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReportsView from './ReportsView.vue'
import { evaluationApi } from '@/api'

const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { runId: '77' } }),
  useRouter: () => router,
}))

vi.mock('@/api', () => ({
  evaluationApi: {
    listRuns: vi.fn(),
    getReport: vi.fn(),
    listResults: vi.fn(),
    listJudgeDetails: vi.fn(),
  },
}))

const api = vi.mocked(evaluationApi)

describe('ReportsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.listRuns.mockResolvedValue([])
    api.getReport.mockResolvedValue(null as never)
    api.listResults.mockResolvedValue([])
  })

  it('loads a report when opened with a run deep link', async () => {
    mount(ReportsView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(api.getReport).toHaveBeenCalledWith(77)
    expect(api.listResults).toHaveBeenCalledWith(77)
  })
})
