/**
 * 验证 KnowledgeView 的主要工作区可见。
 */
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeView from './KnowledgeView.vue'
import { knowledgeApi } from '@/api'

vi.mock('@/api', () => ({
  knowledgeApi: {
    uploadDocument: vi.fn(),
    getDocumentTask: vi.fn(),
    retryDocumentTask: vi.fn(),
    listDocumentTasks: vi.fn(),
    listDocumentKnowledge: vi.fn(),
    importKnowledge: vi.fn(),
    listReviewCandidates: vi.fn(),
    approveHigh: vi.fn(),
    rejectCandidate: vi.fn(),
  },
}))

const api = vi.mocked(knowledgeApi)

describe('KnowledgeView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.listDocumentTasks.mockResolvedValue([])
    api.listDocumentKnowledge.mockResolvedValue({
      task: { taskId: 'empty', status: 'COMPLETED' },
      items: [],
      totalCount: 0,
    })
    api.listReviewCandidates.mockResolvedValue([])
  })

  it('renders document upload and manual import workspaces', () => {
    const wrapper = mount(KnowledgeView, {
      global: { plugins: [createPinia(), ElementPlus] },
    })

    expect(wrapper.text()).toContain('文档上传')
    expect(wrapper.text()).toContain('手动导入知识')
    expect(wrapper.text()).toContain('任务查询')
    expect(wrapper.text()).toContain('评估知识 ID')
  })

  it('loads pending medium knowledge for human review', async () => {
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

    const wrapper = mount(KnowledgeView, {
      global: { plugins: [createPinia(), ElementPlus] },
    })
    await flushPromises()

    expect(api.listReviewCandidates).toHaveBeenCalledWith({
      status: 'HUMAN_REVIEW_PENDING',
      trustLevel: 'MEDIUM',
      limit: 50,
      offset: 0,
    })
    expect(wrapper.text()).toContain('人工审核')
    expect(wrapper.text()).toContain('需要审核的知识')
    expect(wrapper.text()).toContain('通过为高可信')
    expect(wrapper.text()).toContain('驳回')
  })
})
