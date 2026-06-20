import { describe, expect, it } from 'vitest'
import { validateCaseForm, validateDatasetForm } from './formValidation'

describe('evaluation form validation', () => {
  it('requires a meaningful dataset name', () => {
    expect(validateDatasetForm({ name: '  ' })).toEqual({ name: '请输入测试集名称' })
    expect(validateDatasetForm({ name: 'Regression' })).toEqual({})
  })

  it('requires dataset and question for a case', () => {
    expect(validateCaseForm({ datasetId: null, question: '' })).toEqual({
      datasetId: '请选择测试集',
      question: '请输入评估问题',
    })
  })

  it('rejects invalid expected knowledge identifiers and grades', () => {
    expect(validateCaseForm({
      datasetId: 1,
      question: 'question',
      expectedKnowledge: [
        { knowledgeId: 0, relevanceGrade: 1 },
        { knowledgeId: 2, relevanceGrade: 5 },
      ],
    })).toEqual({
      expectedKnowledge: '知识 ID 必须为正整数，相关度必须在 0 到 3 之间',
    })
  })
})
