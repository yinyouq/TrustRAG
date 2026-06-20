import type { ExpectedKnowledgePayload } from '@/api/types'

export function validateDatasetForm(form: { name: string }): Record<string, string> {
  return form.name.trim() ? {} : { name: '请输入测试集名称' }
}

export function validateCaseForm(form: {
  datasetId: number | null
  question: string
  expectedKnowledge?: ExpectedKnowledgePayload[]
}): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!form.datasetId) {
    errors.datasetId = '请选择测试集'
  }
  if (!form.question.trim()) {
    errors.question = '请输入评估问题'
  }
  if (form.expectedKnowledge?.some(item =>
    !Number.isInteger(item.knowledgeId)
    || item.knowledgeId <= 0
    || !Number.isInteger(item.relevanceGrade)
    || item.relevanceGrade < 0
    || item.relevanceGrade > 3,
  )) {
    errors.expectedKnowledge = '知识 ID 必须为正整数，相关度必须在 0 到 3 之间'
  }
  return errors
}
