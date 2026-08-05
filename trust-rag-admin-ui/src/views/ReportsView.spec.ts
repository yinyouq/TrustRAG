/**
 * 验证 ReportsView 的前端行为和边界场景。
 */
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
    api.listRuns.mockResolvedValue([{
      id: 77,
      datasetId: 2,
      runName: '延迟评估',
      runType: 'MANUAL',
      beforeAfterGroup: 'NORMAL',
      status: 'SUCCEEDED',
      totalCount: 3,
      successCount: 3,
      failedCount: 0,
      engineConfigSnapshot: null,
      modelConfigSnapshot: null,
      knowledgeSnapshotTime: null,
      startedAt: '2026-08-05T08:00:00Z',
      finishedAt: '2026-08-05T08:01:00Z',
      errorMessage: null,
      createdBy: 'tester',
      createdAt: '2026-08-05T08:00:00Z',
    }])
    api.getReport.mockResolvedValue({
      id: 11,
      evalRunId: 77,
      datasetId: 2,
      totalCount: 3,
      successCount: 3,
      failedCount: 0,
      avgRecallAt5: 1,
      avgRecallAt10: 1,
      avgPrecisionAt5: 1,
      avgPrecisionAt10: 1,
      avgMrr: 1,
      avgNdcgAt5: 1,
      avgNdcgAt10: 1,
      avgFaithfulness: 1,
      avgAnswerCorrectness: 1,
      avgAnswerRelevance: 1,
      avgHallucinationScore: 0,
      avgLatencyMs: 2000,
      avgLatencyWithJudgeMs: 6500,
      p90LatencyMs: 3000,
      p90LatencyWithJudgeMs: 9000,
      p95LatencyMs: 3200,
      p95LatencyWithJudgeMs: 9800,
      p99LatencyMs: 3600,
      p99LatencyWithJudgeMs: 11000,
      summaryJson: '{}',
      createdAt: '2026-08-05T08:01:00Z',
    })
    api.listResults.mockResolvedValue([])
  })

  it('loads a report when opened with a run deep link', async () => {
    mount(ReportsView, {
      global: {
        plugins: [ElementPlus],
        stubs: { MetricChart: true, ResultDetailDrawer: true },
      },
    })
    await flushPromises()

    expect(api.getReport).toHaveBeenCalledWith(77)
    expect(api.listResults).toHaveBeenCalledWith(77)
  })

  it('expands all latency percentiles from the P90 card', async () => {
    const wrapper = mount(ReportsView, {
      global: {
        plugins: [ElementPlus],
        stubs: { MetricChart: true, ResultDetailDrawer: true },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('6.5 s / 2.0 s')
    expect(wrapper.text()).toContain('点击查看全部')
    expect(wrapper.text()).not.toContain('P95（含 Judge）')

    await wrapper.find('.metric-card--clickable').trigger('click')

    expect(wrapper.text()).toContain('P90（含 Judge）')
    expect(wrapper.text()).toContain('P95（含 Judge）')
    expect(wrapper.text()).toContain('P99（含 Judge）')
    expect(wrapper.text()).toContain('P90（不含 Judge）')
    expect(wrapper.text()).toContain('P95（不含 Judge）')
    expect(wrapper.text()).toContain('P99（不含 Judge）')
    expect(wrapper.text()).toContain('11.0 s')
  })
})
