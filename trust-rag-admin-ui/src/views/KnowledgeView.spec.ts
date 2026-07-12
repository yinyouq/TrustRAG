/**
 * 验证 KnowledgeView 的主要工作区可见。
 */
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
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
    listKnowledge: vi.fn(),
    getKnowledge: vi.fn(),
    downgradeKnowledge: vi.fn(),
    rollbackKnowledge: vi.fn(),
    mergeKnowledge: vi.fn(),
    deleteKnowledge: vi.fn(),
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
    api.listKnowledge.mockResolvedValue([])
  })

  it('renders document upload and manual import workspaces', () => {
    const wrapper = mount(KnowledgeView, {
      global: { plugins: [createPinia(), ElementPlus] },
    })

    expect(wrapper.text()).toContain('文档上传')
    expect(wrapper.text()).toContain('手动导入知识')
    expect(wrapper.text()).toContain('任务查询')
    expect(wrapper.text()).toContain('知识治理操作')
    expect(wrapper.text()).toContain('评估知识 ID')
  })
})
