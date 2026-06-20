import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewView from './OverviewView.vue'
import { evaluationApi } from '@/api'

vi.mock('@/api', () => ({
  evaluationApi: {
    listRuns: vi.fn(),
    getReport: vi.fn(),
  },
}))

const api = vi.mocked(evaluationApi)

describe('OverviewView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders latest quality metrics without converting null to zero', async () => {
    api.listRuns.mockResolvedValue([{
      id: 10,
      datasetId: 2,
      runName: 'Hybrid v2',
      runType: 'MANUAL',
      beforeAfterGroup: 'NORMAL',
      status: 'SUCCEEDED',
      totalCount: 10,
      successCount: 9,
      failedCount: 1,
      engineConfigSnapshot: null,
      modelConfigSnapshot: null,
      knowledgeSnapshotTime: null,
      startedAt: '2026-06-20T10:00:00Z',
      finishedAt: '2026-06-20T10:01:00Z',
      errorMessage: null,
      createdBy: 'tester',
      createdAt: '2026-06-20T10:00:00Z',
    }])
    api.getReport.mockResolvedValue({
      id: 11,
      evalRunId: 10,
      datasetId: 2,
      totalCount: 10,
      successCount: 9,
      failedCount: 1,
      avgRecallAt5: 0.8,
      avgRecallAt10: 0.864,
      avgPrecisionAt5: 0.7,
      avgPrecisionAt10: 0.5,
      avgMrr: 0.75,
      avgNdcgAt5: 0.78,
      avgNdcgAt10: 0.8,
      avgFaithfulness: null,
      avgAnswerCorrectness: null,
      avgAnswerRelevance: null,
      avgHallucinationScore: null,
      avgLatencyMs: 1250,
      p90LatencyMs: 1900,
      summaryJson: '{}',
      createdAt: '2026-06-20T10:01:00Z',
    })

    const wrapper = mount(OverviewView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('86.4%')
    expect(wrapper.text()).toContain('生成质量未启用')
    expect(wrapper.text()).not.toContain('Faithfulness 0.0%')
    expect(wrapper.text()).toContain('9 / 10')
  })

  it('shows onboarding when no run exists', async () => {
    api.listRuns.mockResolvedValue([])

    const wrapper = mount(OverviewView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('尚无评估结果')
  })
})
