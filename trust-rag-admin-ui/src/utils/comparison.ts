/**
 * Before/After 对比校验工具，提前阻止不合法的对比请求。
 */
import type { EvalRun } from '@/api/types'

/**
 * 校验 Before/After 对比的业务前提，避免请求后端后才发现无法比较。
 */
export function validateRunComparison(
  before: EvalRun | null,
  after: EvalRun | null,
): string | null {
  if (!before || !after) {
    return '请选择 Before 和 After 任务'
  }
  if (before.id === after.id) {
    return 'Before 和 After 不能是同一个任务'
  }
  if (before.datasetId !== after.datasetId) {
    return 'Before 和 After 必须来自同一个测试集'
  }
  if (before.beforeAfterGroup !== 'BEFORE' || after.beforeAfterGroup !== 'AFTER') {
    return '请选择正确标记的 BEFORE 和 AFTER 任务'
  }
  return null
}
