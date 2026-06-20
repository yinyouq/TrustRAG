import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CasesView from './CasesView.vue'
import { evaluationApi } from '@/api'

const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { datasetId: '42' } }),
  useRouter: () => router,
}))

vi.mock('@/api', () => ({
  evaluationApi: {
    listDatasets: vi.fn(),
    listCases: vi.fn(),
    getExpectedKnowledge: vi.fn(),
    createCase: vi.fn(),
    updateCase: vi.fn(),
    deleteCase: vi.fn(),
    importCases: vi.fn(),
  },
}))

const api = vi.mocked(evaluationApi)

describe('CasesView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.listDatasets.mockResolvedValue([])
    api.listCases.mockResolvedValue([])
  })

  it('loads cases when opened with a dataset deep link', async () => {
    mount(CasesView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(api.listCases).toHaveBeenCalledWith(42, { onlyEnabled: false, limit: 500 })
  })
})
