/**
 * 验证知识审核页会加载低可信知识、人工审核候选，并能触发晋升任务。
 */
import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeReviewView from './KnowledgeReviewView.vue'
import { knowledgeApi } from '@/api'
import type { KnowledgeItem, PromotionTask } from '@/api/knowledge'

vi.mock('@/api', () => ({
  knowledgeApi: {
    getKnowledge: vi.fn(),
    listKnowledge: vi.fn(),
    listPromotionTasks: vi.fn(),
    listReviewCandidates: vi.fn(),
    approveHigh: vi.fn(),
    rejectCandidate: vi.fn(),
    runPromotion: vi.fn(),
  },
}))

const api = vi.mocked(knowledgeApi)

describe('KnowledgeReviewView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    const lowKnowledge: KnowledgeItem = {
      id: 9101,
      title: '低可信纠错候选',
      content: '用户纠错生成的低可信知识。',
      trustLevel: 'LOW',
      status: 'LOW_PENDING',
      scopeType: 'GLOBAL_CANDIDATE',
      sourceType: 'user_correction',
      sourceRef: 'feedback://1',
      createdAt: '2026-06-20T12:00:00Z',
      updatedAt: '2026-06-20T12:00:00Z',
    }
    const abnormalKnowledge: KnowledgeItem = {
      id: 9201,
      title: '晋升异常：证据不足',
      content: '这条低可信候选缺少足够证据，Promotion 评估后被拒绝。',
      trustLevel: 'LOW',
      status: 'REJECTED',
      scopeType: 'GLOBAL_CANDIDATE',
      sourceType: 'user_correction',
      sourceRef: 'feedback://2',
      rejectReason: 'Source or evidence is insufficient for medium trust',
      createdAt: '2026-06-20T12:00:00Z',
      updatedAt: '2026-06-20T12:00:00Z',
    }
    api.listKnowledge.mockImplementation(async filters => {
      if (filters?.status === 'REJECTED') return [abnormalKnowledge]
      if (filters?.limit === 11) return [lowKnowledge]
      return []
    })
    const task: PromotionTask = {
      id: 501,
      knowledgeId: 9201,
      status: 'SUCCESS',
      taskType: 'LOW_TO_MEDIUM',
      retryCount: 0,
      errorMessage: null,
      startedAt: '2026-06-20T12:00:00Z',
      finishedAt: '2026-06-20T12:00:01Z',
      createdAt: '2026-06-20T12:00:00Z',
    }
    api.listPromotionTasks.mockResolvedValue([task])
    api.getKnowledge.mockResolvedValue(abnormalKnowledge)
    api.listReviewCandidates.mockResolvedValue([{
      id: 7,
      title: '需要审核的知识',
      content: '这段内容需要人工确认后才能进入高可信池。',
      trustLevel: 'MEDIUM',
      status: 'HUMAN_REVIEW_PENDING',
      scopeType: 'GLOBAL',
      sourceType: 'manual',
      sourceRef: 'manual://review',
      createdAt: '2026-06-20T12:00:00Z',
      updatedAt: '2026-06-20T12:00:00Z',
    }])
    api.runPromotion.mockResolvedValue({ createdTasks: 1, processedTasks: 1 })
  })

  it('loads low-trust knowledge and medium review candidates', async () => {
    const wrapper = mount(KnowledgeReviewView, {
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(api.listKnowledge).toHaveBeenCalledWith({
      trustLevel: 'LOW',
      limit: 11,
      offset: 0,
    })
    expect(api.listReviewCandidates).toHaveBeenCalledWith({
      status: 'HUMAN_REVIEW_PENDING',
      trustLevel: 'MEDIUM',
      limit: 50,
      offset: 0,
    })
    expect(api.listPromotionTasks).toHaveBeenCalledWith({
      taskType: 'LOW_TO_MEDIUM',
      knowledgeId: null,
      limit: 10,
      offset: 0,
    })
    expect(wrapper.text()).toContain('晋升任务记录 / 异常结果')
    expect(wrapper.text()).toContain('晋升异常：证据不足')
    expect(wrapper.text()).toContain('9201')
    expect(wrapper.text()).toContain('低可信知识')
    expect(wrapper.text()).toContain('低可信纠错候选')
    expect(wrapper.text()).toContain('人工审核')
    expect(wrapper.text()).toContain('需要审核的知识')
  })

  it('runs promotion and reloads both lists', async () => {
    const wrapper = mount(KnowledgeReviewView, {
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const button = wrapper.findAll('button').find(item => item.text().includes('Promotion'))
    await button?.trigger('click')
    await flushPromises()

    expect(api.runPromotion).toHaveBeenCalledWith(50)
    const lowListCalls = api.listKnowledge.mock.calls.filter(([filters]) => filters?.limit === 11)
    expect(lowListCalls).toHaveLength(2)
    expect(api.listReviewCandidates).toHaveBeenCalledTimes(2)
    expect(api.listPromotionTasks).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('上次执行：创建 1 个任务，处理 1 个任务')
  })
})
