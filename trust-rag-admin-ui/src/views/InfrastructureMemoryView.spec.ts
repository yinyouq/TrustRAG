import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import InfrastructureMemoryView from './InfrastructureMemoryView.vue'
import { infrastructureApi } from '@/api'

vi.mock('@/api', () => ({
  infrastructureApi: {
    getMemorySnapshot: vi.fn(),
  },
}))

const api = vi.mocked(infrastructureApi)

describe('InfrastructureMemoryView', () => {
  afterEach(() => vi.resetAllMocks())

  it('renders only the two retrieval engines and their aggregated Working Set', async () => {
    api.getMemorySnapshot.mockResolvedValue({
      status: 'AVAILABLE',
      message: '仅统计 Milvus 与 OpenSearch 容器。',
      sampledAt: '2026-08-04T08:00:00Z',
      windowMinutes: 60,
      workingSetBytes: 1280 * 1024 * 1024,
      rssBytes: 1100 * 1024 * 1024,
      peakWorkingSetBytes: 1400 * 1024 * 1024,
      services: [
        { service: 'milvus', displayName: 'Milvus', available: true, workingSetBytes: 768 * 1024 * 1024, rssBytes: 650 * 1024 * 1024, peakWorkingSetBytes: 830 * 1024 * 1024 },
        { service: 'opensearch', displayName: 'OpenSearch', available: true, workingSetBytes: 512 * 1024 * 1024, rssBytes: 450 * 1024 * 1024, peakWorkingSetBytes: 570 * 1024 * 1024 },
      ],
      workingSetTrend: [
        { timestamp: '2026-08-04T07:59:00Z', workingSetBytes: 1200 * 1024 * 1024 },
        { timestamp: '2026-08-04T08:00:00Z', workingSetBytes: 1280 * 1024 * 1024 },
      ],
    })

    const wrapper = mount(InfrastructureMemoryView, {
      global: {
        plugins: [ElementPlus],
        stubs: { MetricChart: true },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('基础设施运行内存')
    expect(wrapper.text()).toContain('1.3 GiB')
    expect(wrapper.text()).toContain('Milvus')
    expect(wrapper.text()).toContain('OpenSearch')
    expect(wrapper.findAll('.el-table__body tr')).toHaveLength(2)
    wrapper.unmount()
  })
})
