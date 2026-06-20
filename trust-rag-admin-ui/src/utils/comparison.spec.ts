import { describe, expect, it } from 'vitest'
import type { EvalRun } from '@/api/types'
import { validateRunComparison } from './comparison'

describe('Before/After comparison validation', () => {
  it('rejects missing and identical runs', () => {
    expect(validateRunComparison(null, null)).toBe('请选择 Before 和 After 任务')
    expect(validateRunComparison(run(1, 5, 'BEFORE'), run(1, 5, 'AFTER'))).toBe('Before 和 After 不能是同一个任务')
  })

  it('rejects cross-dataset comparisons', () => {
    expect(validateRunComparison(run(1, 5, 'BEFORE'), run(2, 6, 'AFTER')))
      .toBe('Before 和 After 必须来自同一个测试集')
  })

  it('accepts a before and after run from the same dataset', () => {
    expect(validateRunComparison(run(1, 5, 'BEFORE'), run(2, 5, 'AFTER'))).toBeNull()
  })

  function run(id: number, datasetId: number, group: 'BEFORE' | 'AFTER'): EvalRun {
    return {
      id, datasetId, runName: `run-${id}`, runType: 'BEFORE_AFTER', beforeAfterGroup: group,
      status: 'SUCCEEDED', totalCount: 1, successCount: 1, failedCount: 0,
      engineConfigSnapshot: null, modelConfigSnapshot: null, knowledgeSnapshotTime: null,
      startedAt: null, finishedAt: null, errorMessage: null, createdBy: null,
      createdAt: '2026-06-20T00:00:00Z',
    }
  }
})
